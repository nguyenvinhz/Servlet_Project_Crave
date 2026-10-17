// Browser regressions for checkout address search and receiving-point selection.
// Uses the actual checkout JSP, JS and Leaflet with a loopback HTML/form fixture.
// No database, external geocoder, customer account or order creation is required.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(file => fs.existsSync(file));
assert(chrome, 'Set CHROME_PATH to Chrome or Edge.');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-delivery-map-'));
const artifacts = path.join(project, '.runtime', 'map-browser');
fs.mkdirSync(artifacts, {recursive: true});
const unavailableMessage = 'Không kết nối được dịch vụ tìm địa chỉ lúc này. Bạn có thể ghim điểm nhận trên bản đồ để tiếp tục.';
const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const errors = [], calls = [], pending = new Map();
let browser, socket, sessionId, nextId = 1, checks = 0, base;
const send = (method, params = {}, target = sessionId) => new Promise((resolve, reject) => {
    const id = nextId++;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP timed out: ' + method)); }, 15000);
    pending.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method, params, ...(target ? {sessionId: target} : {})}));
});
async function evaluate(expression) {
    const response = await send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text);
    return response.result.value;
}
async function waitFor(expression, reason) {
    for (let attempt = 0; attempt < 100; attempt++) {
        if (await evaluate(expression)) return;
        await sleep(40);
    }
    throw new Error(reason);
}
function pass(message) { checks++; process.stdout.write('PASS ' + message + '\n'); }
function checkoutHtml(saved = false) {
    let source = fs.readFileSync(path.join(project, 'frontend/WEB-INF/views/order/checkout.jsp'), 'utf8');
    source = source.replace(/<%@[^]*?%>/g, '')
        .replace(/<c:url value='([^']*)'\s*\/>/g, '$1')
        .replace(/<c:forEach\b[^]*?<\/c:forEach>/g, '')
        .replace(/<c:out\b[^]*?\/>/g, '')
        .replace(/<(?:c|fmt):[^>]*>/g, '')
        .replace('data-subtotal="${subtotal}"', 'data-subtotal="100000"')
        .replace('data-discount="${discount}"', 'data-discount="0"')
        .replace('data-empty="${empty cart.items}"', 'data-empty="false"')
        .replace('data-store-latitude="${store.latitude()}"', 'data-store-latitude="10.8925871"')
        .replace('data-store-longitude="${store.longitude()}"', 'data-store-longitude="106.7637161"')
        .replace('data-configured="${deliveryConfigured}"', 'data-configured="true"')
        .replace(/\$\{[^}]*}/g, '');
    if (saved) source = source.replace(/(<select id="savedAddressSelect"[^>]*>)/,
            '$1<option value="12 saved street" data-note="Call reception" data-default="true" selected>★ [Mặc định] 12 saved street (Call reception)</option>')
        .replace('id="deliveryAddress" value=""', 'id="deliveryAddress" value="12 saved street"');
    return '<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/assets/css/styles.css">'
        + '<link rel="stylesheet" href="/assets/css/order.css"></head><body>' + source + '</body></html>';
}
const server = http.createServer(async (request, response) => {
    const route = new URL(request.url, 'http://localhost').pathname;
    if (route === '/checkout') {
        response.setHeader('Content-Type', 'text/html;charset=UTF-8');
        response.end(checkoutHtml(new URL(request.url, 'http://localhost').searchParams.has('saved'))); return;
    }
    if (route.startsWith('/assets/')) {
        const file = path.resolve(project, 'frontend', '.' + route);
        if (!file.startsWith(path.resolve(project, 'frontend/assets') + path.sep) || !fs.existsSync(file)) {
            response.writeHead(404); response.end(); return;
        }
        response.setHeader('Content-Type', file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : 'application/octet-stream');
        response.end(fs.readFileSync(file)); return;
    }
    if (request.method !== 'POST' || !route.startsWith('/delivery/')) {
        response.writeHead(404); response.end(); return;
    }
    let body = '';
    for await (const chunk of request) body += chunk;
    const values = new URLSearchParams(body);
    calls.push({route, values: Object.fromEntries(values), contentType: request.headers['content-type']});
    response.setHeader('Content-Type', 'text/html;charset=UTF-8');
    if (route === '/delivery/search') {
        const address = values.get('address');
        await sleep(address === 'slow search' ? 350 : 15);
        if (address === 'provider unavailable') {
            response.writeHead(503); response.end('<p data-delivery-error>' + unavailableMessage + '</p>'); return;
        }
        const single = '<button type="button" class="button outline delivery-search-result" data-latitude="10.8931" data-longitude="106.7642" data-approximate="' + (address === '10.8931, 106.7642' ? 'false' : 'true') + '">12 Võ Văn Ngân, Thủ Đức</button>';
        response.end(address === 'no results' ? '' : single + (address === 'multiple places'
            ? '<button type="button" class="button outline delivery-search-result" data-latitude="10.91" data-longitude="106.78" data-approximate="true">Một địa chỉ khác, Thủ Đức</button>' : ''));
    } else if (route === '/delivery/reverse') {
        await sleep(60); response.end('<span data-location-label>Địa chỉ gần ghim đã chọn</span>');
    } else if (route === '/delivery/quote') {
        response.end('<span data-quote data-deliverable="true" data-fee="15000">Phí giao hàng 15.000 đ</span>');
    } else { response.writeHead(404); response.end(); }
});
async function editAddress(value) {
    await evaluate('(()=>{const input=document.getElementById("deliveryAddress");input.value=' + JSON.stringify(value) + ';input.dispatchEvent(new Event("input",{bubbles:true}));})()');
}
async function clickMap() {
    await evaluate('document.getElementById("deliveryMap").scrollIntoView({block:"center"})');
    const point = await evaluate('(()=>{const r=document.getElementById("deliveryMap").getBoundingClientRect();return {x:r.x+r.width*.61,y:r.y+r.height*.52};})()');
    await send('Input.dispatchMouseEvent', {type: 'mousePressed', ...point, button: 'left', clickCount: 1});
    await send('Input.dispatchMouseEvent', {type: 'mouseReleased', ...point, button: 'left', clickCount: 1});
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'Map click did not select a point.');
}
async function selectType(value) {
    await evaluate('(()=>{const input=document.getElementById("fulfillmentType");input.value=' + JSON.stringify(value) + ';input.dispatchEvent(new Event("change",{bubbles:true}));})()');
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    base = 'http://127.0.0.1:' + server.address().port;
    const debuggingUrl = await new Promise((resolve, reject) => {
        browser = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions',
            '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0', '--user-data-dir=' + work, 'about:blank'],
        {windowsHide: true, stdio: ['ignore', 'ignore', 'pipe']});
        let output = '';
        const timer = setTimeout(() => reject(new Error('Chrome did not start.')), 15000);
        browser.stderr.on('data', bytes => {
            output += bytes;
            const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
            if (match) { clearTimeout(timer); resolve(match[1]); }
        });
        browser.on('error', reject);
    });
    socket = new WebSocket(debuggingUrl);
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id) {
            const entry = pending.get(message.id);
            if (!entry) return;
            pending.delete(message.id); clearTimeout(entry.timer);
            if (message.error) entry.reject(new Error(message.error.message)); else entry.resolve(message.result);
        } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.exception?.description || message.params.exceptionDetails.text);
    };
    const target = await send('Target.createTarget', {url: 'about:blank'}, null);
    sessionId = (await send('Target.attachToTarget', {targetId: target.targetId, flatten: true}, null)).sessionId;
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Network.setBlockedURLs', {urls: [base + '/map/tiles/*']});
    await send('Page.addScriptToEvaluateOnNewDocument', {source: 'window.mapRegressionGeolocation=[];navigator.geolocation.getCurrentPosition=(success,failure)=>window.mapRegressionGeolocation.push({success,failure});'});
    await send('Emulation.setDeviceMetricsOverride', {width: 1365, height: 1000, deviceScaleFactor: 1, mobile: false});
    await send('Page.navigate', {url: base + '/checkout'});
    await waitFor('Boolean(document.getElementById("deliveryMap")?.classList.contains("leaflet-container"))', 'Checkout map did not initialize.');

    // Enter in the address field must request search, never submit an order form.
    await evaluate('window.orderFormSubmits=0;document.getElementById("checkoutForm").addEventListener("submit",event=>{event.preventDefault();window.orderFormSubmits++;});');
    await editAddress('12 Vo Van Ngan, Thu Duc');
    await evaluate('document.getElementById("deliveryAddress").focus()');
    await send('Input.dispatchKeyEvent', {type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13});
    await send('Input.dispatchKeyEvent', {type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13});
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'Enter did not search and pin the typed address.');
    assert.equal(await evaluate('window.orderFormSubmits'), 0, 'Address Enter attempted to submit an order.');
    assert.equal(await evaluate('document.querySelectorAll(".delivery-search-result").length'), 0, 'A unique result unnecessarily required another selection.');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value'), '10.8931000');
    assert.equal(await evaluate('document.getElementById("deliveryLongitude").value'), '106.7642000');
    assert.equal(await evaluate('document.getElementById("deliveryAddress").value'), '12 Vo Van Ngan, Thu Duc (10.893100, 106.764200)', 'Selecting a provider suggestion changed the customer\'s house number or address.');
    assert.equal(await evaluate('document.getElementById("matchedDeliveryAddress").textContent'), '12 Võ Văn Ngân, Thủ Đức');
    assert.equal(await evaluate('document.getElementById("confirmDeliveryLocationBtn").hidden'), false, 'An approximate street suggestion bypassed confirmation.');
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), true);
    assert.match(await evaluate('document.getElementById("summaryTotal").textContent'), /100\.000\s+đ \+ phí giao hàng/, 'The summary should retain the visible item total while a receiving pin is unconfirmed.');
    await evaluate('document.getElementById("confirmDeliveryLocationBtn").click()');
    await waitFor('!document.getElementById("placeOrderBtn").disabled', 'Selected search result did not receive a delivery quote.');
    pass('Enter searches and pins one result; the typed house number survives explicit receiving-point confirmation');

    await editAddress('a different address');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value'), '');
    assert.equal(await evaluate('document.querySelectorAll(".delivery-pin:not(.delivery-store-pin)").length'), 0, 'Address edit left an obsolete receiving pin.');
    assert.equal(await evaluate('document.getElementById("confirmDeliveryLocationBtn").hidden'), true, 'Address edit left a nonfunctional confirmation button.');
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), true);
    pass('Editing the address invalidates the old pin, coordinates and quote');

    await editAddress('slow search');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await editAddress('12 Vo Van Ngan, Thu Duc');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'An obsolete search prevented searching the new address.');
    assert.equal(calls.filter(call => call.route === '/delivery/search').at(-1).values.address, '12 Vo Van Ngan, Thu Duc');
    pass('Changing the query allows an immediate new search while the old request is pending');

    await editAddress('slow search');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await clickMap();
    const selected = await evaluate('document.getElementById("deliveryLatitude").value + "," + document.getElementById("deliveryLongitude").value');
    await sleep(450);
    assert.equal(await evaluate('document.querySelectorAll(".delivery-search-result").length'), 0, 'Late search results replaced a newer map choice.');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value + "," + document.getElementById("deliveryLongitude").value'), selected);
    pass('A late search response cannot replace a newer map selection');

    await editAddress('slow search');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await selectType('PICKUP'); await sleep(450);
    assert.equal(await evaluate('document.getElementById("addressGroup").hidden'), true);
    assert.equal(await evaluate('document.querySelectorAll(".delivery-search-result").length'), 0);
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), false);
    await selectType('DELIVERY');
    pass('Pickup ignores pending address searches and keeps pickup checkout available');

    await editAddress('no results');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await waitFor('!document.getElementById("findDeliveryAddressBtn").disabled', 'Empty search left the search button locked.');
    assert.equal(await evaluate('document.querySelectorAll(".delivery-search-result").length'), 0);
    await clickMap();
    await evaluate('document.getElementById("confirmDeliveryLocationBtn").click()');
    await waitFor('!document.getElementById("placeOrderBtn").disabled', 'Manual map fallback did not recover after empty search.');
    pass('An empty search allows map selection and confirmation');

    await editAddress('provider unavailable');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await waitFor('document.getElementById("deliveryLocationStatus").textContent === ' + JSON.stringify(unavailableMessage), 'Search hid the provider connection message behind a generic error.');
    assert.equal(await evaluate('document.getElementById("findDeliveryAddressBtn").disabled'), false);
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), true);
    pass('A provider outage shows the HTML error fragment and permits another search');

    await evaluate('document.getElementById("useMyLocationBtn").click()');
    await editAddress('12 Vo Van Ngan, Thu Duc');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'A new search did not recover.');
    await evaluate('window.mapRegressionGeolocation.at(-1).success({coords:{latitude:10.90,longitude:106.78}})');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value'), '10.8931000', 'A late geolocation callback replaced the address the user selected.');
    pass('A late geolocation response cannot replace a newer searched address');

    const beforeTyping = calls.filter(call => call.route === '/delivery/search').length;
    await editAddress('a'); await sleep(1250);
    assert.equal(calls.filter(call => call.route === '/delivery/search').length, beforeTyping, 'Short input triggered an unnecessary provider lookup.');
    await editAddress('12 typed street'); await sleep(600); await editAddress('34 typed street');
    await sleep(700);
    assert.equal(calls.filter(call => call.route === '/delivery/search').length, beforeTyping, 'Typing was not debounced.');
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'Pausing after typing did not search and pin automatically.');
    assert.equal(calls.filter(call => call.route === '/delivery/search').at(-1).values.address, '34 typed street');
    pass('Typing is debounced for 1.2 seconds and automatically places a unique suggestion');

    await editAddress('multiple places');
    await evaluate('document.getElementById("deliveryAddress").dispatchEvent(new Event("change",{bubbles:true}))');
    await waitFor('document.querySelectorAll(".delivery-search-result").length === 2', 'Changing the address did not show the candidate choices.');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value'), '', 'Multiple candidates silently selected an address.');
    assert.equal(await evaluate('document.querySelectorAll(".delivery-search-result.button.outline").length'), 2, 'Candidate buttons lost their original styling.');
    await evaluate('document.querySelectorAll(".delivery-search-result")[1].click()');
    assert.equal(await evaluate('document.getElementById("deliveryLatitude").value'), '10.9100000');
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), true);
    pass('Address change searches immediately; multiple styled choices preserve customer selection and confirmation');

    await editAddress('10.8931, 106.7642');
    await evaluate('document.getElementById("findDeliveryAddressBtn").click()');
    await waitFor('!document.getElementById("placeOrderBtn").disabled', 'An exact coordinate did not receive its delivery quote automatically.');
    pass('An explicit exact coordinate resolves and quotes without an approximate-address confirmation');

    await send('Page.navigate', {url: base + '/checkout?saved=1'});
    await waitFor('Boolean(document.getElementById("deliveryLatitude")?.value)', 'The saved default address was not resolved on page load.');
    assert.equal(await evaluate('document.getElementById("customerNote").value'), 'Call reception');
    assert.match(await evaluate('document.getElementById("addressHint").textContent'), /địa chỉ mặc định/);
    await evaluate('document.getElementById("savedAddressSelect").insertAdjacentHTML("beforeend",\'<option value="other saved address" data-note="Ring the bell">📍 other saved address</option>\')');
    await evaluate('(()=>{const select=document.getElementById("savedAddressSelect");select.value="other saved address";select.dispatchEvent(new Event("change",{bubbles:true}));})()');
    await waitFor('Boolean(document.getElementById("deliveryLatitude").value)', 'Choosing a saved address did not locate it immediately.');
    assert.equal(calls.filter(call => call.route === '/delivery/search').at(-1).values.address, 'other saved address');
    assert.equal(await evaluate('document.getElementById("customerNote").value'), 'Ring the bell');
    pass('Saved/default addresses resolve automatically and carry their existing delivery notes');

    assert(calls.length >= 8, 'Expected real form requests to the loopback fixture.');
    assert(calls.every(call => call.contentType?.startsWith('application/x-www-form-urlencoded')), 'Map requests must use normal form encoding.');
    assert.deepEqual(errors, [], 'Uncaught browser JavaScript errors.');
    const screenshot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false});
    fs.writeFileSync(path.join(artifacts, 'search-regression.png'), Buffer.from(screenshot.data, 'base64'));
    pass('All map requests use form encoding; no browser exceptions or order creation');
    process.stdout.write('Completed ' + checks + ' map browser scenarios.\n');
})().catch(async error => {
    process.stderr.write('FAIL ' + error.message + '\n'); process.exitCode = 1;
    if (sessionId && socket?.readyState === WebSocket.OPEN) {
        try {
            const screenshot = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false});
            fs.writeFileSync(path.join(artifacts, 'failure.png'), Buffer.from(screenshot.data, 'base64'));
        } catch {}
    }
}).finally(async () => {
    if (socket?.readyState === WebSocket.OPEN) {
        try { await send('Browser.close', {}, null); } catch {}
        socket.close();
    }
    browser?.kill();
    server.closeAllConnections(); server.close();
    await sleep(250);
    const resolved = path.resolve(work), prefix = path.resolve(os.tmpdir()) + path.sep;
    if (resolved.startsWith(prefix) && path.basename(resolved).startsWith('crave-delivery-map-')) {
        try { fs.rmSync(resolved, {recursive: true, force: true, maxRetries: 5, retryDelay: 100}); } catch {}
    }
});
