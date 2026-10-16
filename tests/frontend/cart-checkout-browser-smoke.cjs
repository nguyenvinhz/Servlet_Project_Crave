// Run against a disposable running app and a customer whose cart is empty.
// CRAVE_SMOKE_BASE_URL defaults to http://127.0.0.1:8081/crave.
// Set CRAVE_SMOKE_CUSTOMER_EMAIL / CRAVE_SMOKE_CUSTOMER_PASSWORD and optionally CHROME_PATH.
// Uses Chrome DevTools to exercise actual rendered JSPs and native form navigation.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8081/crave').replace(/\/$/, '');
const email = process.env.CRAVE_SMOKE_CUSTOMER_EMAIL;
const password = process.env.CRAVE_SMOKE_CUSTOMER_PASSWORD;
assert(email && password, 'Set CRAVE_SMOKE_CUSTOMER_EMAIL and CRAVE_SMOKE_CUSTOMER_PASSWORD for a disposable customer.');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
].find(fs.existsSync);
assert(chrome, 'Set CHROME_PATH to Chrome or Edge.');
const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-servlet-browser-'));
const profile = path.join(artifacts, 'profile');
const browser = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run',
    '--disable-extensions', '--disable-background-networking', '--remote-debugging-port=0', '--user-data-dir=' + profile,
    '--host-resolver-rules=MAP tile.openstreetmap.org ~NOTFOUND', 'about:blank'], {windowsHide:true});
browser.stdout.resume(); browser.stderr.resume();
let startupError;
browser.on('error', error => { startupError = error; });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket;
let checks = 0;
let send;
const requests = [];
const errors = [];
async function waitFor(predicate, message) {
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
        if (startupError) throw startupError;
        try { if (await predicate()) return; } catch (_) { /* Navigation replaces the JavaScript context. */ }
        await pause(50);
    }
    throw new Error(message || 'Browser condition timed out');
}
async function evaluate(expression) {
    const result = await send('Runtime.evaluate', {expression, returnByValue:true, awaitPromise:true});
    if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + result.exceptionDetails.exception?.description);
    return result.result.value;
}
async function ready(selector) {
    await waitFor(async () => evaluate(`document.readyState === 'complete' && Boolean(document.querySelector(${JSON.stringify(selector)}))`), 'Missing rendered selector: ' + selector);
}
async function navigate(route, selector) {
    await evaluate('window.__smokeNavigating = true');
    await send('Page.navigate', {url:route.startsWith('http') ? route : base + route});
    await waitFor(async () => evaluate('!window.__smokeNavigating'), 'Document navigation did not finish');
    await ready(selector);
}
async function submit(selector, buttonSelector, expectedPath, expectedSelector) {
    const count = requests.length;
    await evaluate(`window.__smokeSubmitting = true;document.querySelector(${JSON.stringify(selector)}).requestSubmit(${buttonSelector ? `document.querySelector(${JSON.stringify(buttonSelector)})` : ''})`);
    await waitFor(async () => requests.length > count && evaluate(`!window.__smokeSubmitting && location.pathname.endsWith(${JSON.stringify(expectedPath)}) && document.readyState === 'complete' && Boolean(document.querySelector(${JSON.stringify(expectedSelector)}))`), 'Form did not navigate to ' + expectedPath);
    checks++;
}
async function screenshot(name) {
    const result = await send('Page.captureScreenshot', {format:'png', captureBeyondViewport:false});
    fs.writeFileSync(path.join(artifacts, name + '.png'), Buffer.from(result.data, 'base64'));
}
(async () => {
    const portFile = path.join(profile, 'DevToolsActivePort');
    await waitFor(() => fs.existsSync(portFile), 'Chrome did not start');
    const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
    const targets = await (await fetch('http://127.0.0.1:' + port + '/json')).json();
    socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
    let id = 0;
    const pending = new Map();
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (pending.has(message.id)) {
            const handler = pending.get(message.id); pending.delete(message.id);
            clearTimeout(handler.timer);
            message.error ? handler.reject(new Error(message.error.message)) : handler.resolve(message.result);
        }
        if (message.method === 'Network.requestWillBeSent') requests.push(message.params);
        if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
    };
    send = (method, params = {}) => new Promise((resolve, reject) => {
        const requestId = ++id;
        const timer = setTimeout(() => { pending.delete(requestId); reject(new Error('Browser command timed out: ' + method)); }, 15000);
        pending.set(requestId, {resolve, reject, timer});
        socket.send(JSON.stringify({id:requestId, method, params}));
    });
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Network.setBlockedURLs', {urls:['https://fonts.googleapis.com/*', 'https://fonts.gstatic.com/*', 'https://*.tile.openstreetmap.org/*']});
    await send('Emulation.setDeviceMetricsOverride', {width:1280,height:1000,deviceScaleFactor:1,mobile:false});
    await navigate('/auth/login', 'form');
    await evaluate(`document.querySelector('[name=email]').value=${JSON.stringify(email)};document.querySelector('[name=password]').value=${JSON.stringify(password)};`);
    await submit('form', null, '/menu', '.clean-food-grid');
    await navigate('/cart', '.cart-container');
    assert.equal(await evaluate('document.querySelectorAll(".cart-item-card").length'), 0, 'Use a disposable customer with an empty cart.');
    await navigate('/menu', '.clean-food-card');
    const detail = await evaluate('document.querySelector(".clean-food-card:not(.food-card-unavailable) .clean-card-title a")?.href');
    assert(detail, 'The seeded menu has no available food.');
    await navigate(detail, '#orderCustomizationForm');
    assert(await evaluate(`!document.querySelector('.options-title') || document.querySelector('.options-title').textContent.trim() === 'Tùy chọn cho món ăn'`), 'Food option labels are not UTF-8.');
    await evaluate(`document.getElementById('orderQuantity').value='2';document.getElementById('orderNote').value='Ít cay';document.getElementById('orderQuantity').dispatchEvent(new Event('input',{bubbles:true}));`);
    const detailTotal = await evaluate('document.getElementById("totalPriceDisplay").textContent');
    assert(!detailTotal.includes('NaN'), 'Local price preview is invalid.');
    await submit('#orderCustomizationForm', '#addToCartBtn', '/cart', '.cart-item-card');
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent.trim()'), '2');
    assert(await evaluate('document.querySelector(".cart-item-note").textContent.includes("Ít cay")'));
    checks += 2;
    await submit('#logoutForm', '#customerLogoutBtn', '/auth/login', '#loginForm');
    await evaluate(`document.querySelector('[name=email]').value=${JSON.stringify(email)};document.querySelector('[name=password]').value=${JSON.stringify(password)};`);
    await submit('#loginForm', null, '/menu', '.clean-food-grid');
    assert.equal(await evaluate('document.querySelector(".cart-badge-count").textContent.trim()'), '2', 'Fresh login lost the persisted cart badge.');
    checks++;
    await navigate('/cart', '.cart-item-card');
    const edit = await evaluate('document.querySelector(".cart-item-edit-btn").href');
    await navigate(edit, '#cartItemEditorForm');
    assert(await evaluate(`!document.querySelector('#cartItemEditor .options-title') || document.querySelector('#cartItemEditor .options-title').textContent.trim() === 'Tùy chọn cho món ăn'`), 'Cart option labels are not UTF-8.');
    await evaluate(`document.getElementById('cartEditorQuantity').value='3';document.getElementById('cartEditorNote').value='Không hành';document.getElementById('cartEditorQuantity').dispatchEvent(new Event('input',{bubbles:true}));`);
    assert(!await evaluate('document.getElementById("cartEditorTotal").textContent.includes("NaN")'));
    await evaluate(`document.querySelector('#cartItemEditorForm button[type=submit]').scrollIntoView({block:'center'})`);
    assert(await evaluate(`(() => {
        const button = document.querySelector('#cartItemEditorForm button[type=submit]');
        const bounds = button.getBoundingClientRect();
        return document.elementFromPoint(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)?.closest('button') === button;
    })()`), 'Cart rows cover the customization Save button.');
    await screenshot('cart-editor-desktop');
    await submit('#cartItemEditorForm', '#cartItemEditorForm button[type=submit]', '/cart', '.cart-item-card');
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent.trim()'), '3');
    assert(await evaluate('document.querySelector(".cart-item-note").textContent.includes("Không hành")'));
    const editPost = requests.findLast(call => call.request.method === 'POST' && call.request.url.endsWith('/cart/items'));
    const editForm = new URLSearchParams(editPost.request.postData);
    assert.equal(editForm.get('customization'), 'true');
    assert.equal(editForm.get('quantity'), '3');
    assert.equal(editForm.get('note'), 'Không hành');
    checks += 3;
    await submit('form.cart-stepper', 'form.cart-stepper button:first-of-type', '/cart', '.cart-item-card');
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent.trim()'), '2');
    assert(await evaluate('document.querySelector(".cart-item-note").textContent.includes("Không hành")'), 'Changing quantity erased the note.');
    checks += 2;
    await send('Emulation.setDeviceMetricsOverride', {width:390,height:844,deviceScaleFactor:1,mobile:true});
    await navigate(edit, '#cartItemEditorForm');
    assert(await evaluate('document.getElementById("cartItemEditorForm").getBoundingClientRect().width <= innerWidth'), 'Cart editor overflows mobile viewport.');
    await screenshot('cart-editor-mobile');
    await navigate('/checkout', '#checkoutForm');
    assert.equal(await evaluate('document.getElementById("checkoutForm").method'), 'post');
    assert(await evaluate(`(() => {
        const address = document.getElementById('deliveryAddress');
        address.value = ''; address.dispatchEvent(new Event('input', {bubbles:true}));
        const map = document.getElementById('deliveryMap');
        const bounds = map.getBoundingClientRect();
        map.dispatchEvent(new MouseEvent('click', {bubbles:true, clientX:bounds.x + bounds.width / 2, clientY:bounds.y + bounds.height / 2}));
        const latitude = document.getElementById('deliveryLatitude').value;
        const longitude = document.getElementById('deliveryLongitude').value;
        return !!latitude && !!longitude && address.value === Number(latitude).toFixed(6) + ', ' + Number(longitude).toFixed(6);
    })()`), 'A manual pin with an empty address did not provide coordinate fallback text.');
    assert(await evaluate(`(() => {
        let receivedOptions;
        Object.defineProperty(navigator, 'geolocation', {configurable:true, value:{getCurrentPosition:(success, failure, options) => {
            receivedOptions = options; success({coords:{latitude:10.885, longitude:106.775}});
        }}});
        document.getElementById('useMyLocationBtn').click();
        return receivedOptions?.enableHighAccuracy === true && receivedOptions.maximumAge === 0
            && document.getElementById('deliveryAddress').value === '10.885000, 106.775000';
    })()`), 'Device location did not request a fresh fix or update the coordinate address.');
    checks += 2;
    await evaluate(`const select=document.getElementById('fulfillmentType');select.value='PICKUP';select.dispatchEvent(new Event('change'));`);
    assert(await evaluate('document.getElementById("addressGroup").hidden'));
    await submit('#checkoutForm', '#checkoutForm button[value=quote]', '/checkout', '#checkoutForm');
    assert.equal(await evaluate('document.getElementById("fulfillmentType").value'), 'PICKUP');
    assert(await evaluate('document.querySelector(".order-summary").textContent.includes("0")'));
    checks += 3;
    await screenshot('checkout-pickup-mobile');
    await navigate('/promotions', '.promos-container');
    assert(!await evaluate('Boolean(document.querySelector("#promosLoadingState"))'), 'Promotions still depend on a loading API.');
    await navigate('/cart', '.cart-item-card');
    await evaluate('window.confirm = () => true');
    await submit('form[data-confirm]', 'form[data-confirm] button', '/cart', '.cart-empty-state');
    const appRequests = requests.filter(call => call.request.url.startsWith(base));
    assert(!appRequests.some(call => /\/api\//.test(call.request.url)), 'Browser requested a removed API route.');
    assert(!appRequests.some(call => call.type === 'Fetch' || call.type === 'XHR'), 'Application used a browser data API instead of native forms.');
    assert.equal(errors.length, 0, 'Browser JavaScript exception: ' + errors.map(error => error.text).join('; '));
    console.log(`Servlet cart/checkout browser: ${checks} checks passed. Artifacts: ${artifacts}`);
})().catch(async error => {
    console.error(error);
    if (send) {
        try {
            await screenshot('failure');
            fs.writeFileSync(path.join(artifacts, 'failure.html'), await evaluate('document.documentElement.outerHTML'));
        } catch (_) { }
    }
    console.error('Browser diagnostics: ' + artifacts);
    process.exitCode=1;
}).finally(() => {socket?.close(); browser.kill();});
