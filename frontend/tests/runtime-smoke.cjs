// Node 22+ and Chrome/Edge. Exercises the deployed WAR without API or JSP fixtures.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn} = require('node:child_process');
const {randomUUID} = require('node:crypto');

const base = (process.env.SMOKE_BASE_URL || 'http://127.0.0.1:8080/crave').replace(/\/$/, '');
const baseUrl = new URL(base);
assert(['127.0.0.1', 'localhost'].includes(baseUrl.hostname), 'Use a disposable local application.');
const chrome = process.env.CHROME_PATH || [
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
].find(candidate => fs.existsSync(candidate));
assert(chrome, 'Set CHROME_PATH to Chrome or Edge.');
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-runtime-browser-'));
const artifacts = process.env.SMOKE_ARTIFACTS_DIR || path.join(profile, 'artifacts');
fs.mkdirSync(artifacts, {recursive: true});
const browserLog = fs.openSync(path.join(artifacts, 'chrome.log'), 'w');
const browser = spawn(chrome, [
    '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-background-networking',
    '--disable-extensions', '--no-first-run', '--no-default-browser-check',
    '--user-data-dir=' + profile, '--remote-debugging-port=0', 'about:blank'
], {stdio: ['ignore', 'ignore', browserLog]});
fs.closeSync(browserLog);
let startupError, socket, sequence = 0;
browser.on('error', error => { startupError = error; });
const pending = new Map();
const checks = [];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function send(method, params = {}) {
    return new Promise((resolve, reject) => {
        const id = ++sequence;
        const timer = setTimeout(() => {
            pending.delete(id);
            reject(new Error('Chrome command timed out: ' + method));
        }, 15000);
        pending.set(id, response => {
            clearTimeout(timer);
            response.error ? reject(new Error(response.error.message)) : resolve(response.result);
        });
        socket.send(JSON.stringify({id, method, params}));
    });
}

async function evaluate(expression) {
    const response = await send('Runtime.evaluate', {expression, awaitPromise: true, returnByValue: true});
    if (response.exceptionDetails) {
        throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text);
    }
    return response.result.value;
}

async function waitFor(expression, description) {
    const deadline = Date.now() + 30000;
    let lastError;
    while (Date.now() < deadline) {
        try { if (await evaluate(expression)) return; }
        catch (error) { lastError = error; }
        await delay(100);
    }
    throw new Error('Timed out: ' + description + (lastError ? '; ' + lastError.message : ''));
}

async function navigate(route, selector) {
    const target = new URL(base + route);
    await send('Page.navigate', {url: target.href});
    await waitFor(`location.pathname === ${JSON.stringify(target.pathname)} && location.search === ${JSON.stringify(target.search)} &&
        document.readyState === 'complete' && document.querySelector(${JSON.stringify(selector)}) !== null`,
    'render JSP ' + route);
}

async function api(route, method = 'GET', body) {
    const options = {method, credentials: 'same-origin'};
    if (body !== undefined) {
        options.headers = {'Content-Type': 'application/json'};
        options.body = JSON.stringify(body);
    }
    return evaluate(`(async () => {
        const response = await fetch(${JSON.stringify(base + route)}, ${JSON.stringify(options)});
        return {status: response.status, body: await response.json()};
    })()`);
}

function expectApi(response, status) {
    assert.equal(response.status, status, JSON.stringify(response.body));
    assert.equal(response.body.success, status < 400, JSON.stringify(response.body));
    return response.body.data;
}

function passed(name) {
    checks.push(name);
    console.log('PASS ' + name);
}

async function connect() {
    const deadline = Date.now() + 30000;
    let target;
    while (Date.now() < deadline && !target) {
        if (startupError) throw startupError;
        try {
            const port = fs.readFileSync(path.join(profile, 'DevToolsActivePort'), 'utf8').split('\n')[0];
            assert(/^\d+$/.test(port));
            const targets = await fetch('http://127.0.0.1:' + port + '/json',
                {signal: AbortSignal.timeout(2000)}).then(response => response.json());
            target = targets.find(candidate => candidate.type === 'page');
        } catch (_) { await delay(100); }
    }
    assert(target, 'Chrome debugging endpoint did not start.');
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
        socket.addEventListener('open', resolve, {once: true});
        socket.addEventListener('error', reject, {once: true});
    });
    socket.addEventListener('message', event => {
        const response = JSON.parse(event.data);
        if (response.id) {
            pending.get(response.id)?.(response);
            pending.delete(response.id);
        }
    });
    await send('Page.enable');
    await send('Network.enable');
    // Remote fonts and seed image URLs are outside this local application's test.
    await send('Network.setBlockedURLs', {urls: ['*fonts.googleapis.com*', '*fonts.gstatic.com*', '*res.cloudinary.com*']});
}

async function run() {
    const health = await fetch(base + '/api/health', {signal: AbortSignal.timeout(5000)});
    const healthBody = await health.json();
    assert.equal(health.status, 200);
    assert.equal(healthBody.success, true);
    assert.equal(healthBody.data.application, 'UP');
    assert.equal(healthBody.data.database, 'UP');
    passed('application-and-database-health');

    await connect();
    await navigate('/menu', 'main');
    assert(await evaluate('document.body.textContent.includes("CI smoke bowl")'), 'Menu did not render database fixtures.');
    await navigate('/menu/detail?id=CI_FOOD', '#foodName');
    assert(await evaluate('document.body.textContent.includes("CI smoke topping")'), 'Food detail did not render options.');
    passed('real-menu-and-detail-jsp');
    expectApi(await api('/api/cart'), 401);
    passed('guest-protected-api');

    const token = randomUUID().replaceAll('-', '');
    const email = 'smoke-' + token + '@example.test';
    const phone = '09' + String(Math.floor(Math.random() * 100000000)).padStart(8, '0');
    const fields = {fullName: 'CI Smoke Tester', email, phone, password: 'SmokeTest#123', confirmPassword: 'SmokeTest#123'};
    await navigate('/auth/register', '#registerForm');
    await evaluate(`(() => {
        for (const [id, value] of Object.entries(${JSON.stringify(fields)})) document.getElementById(id).value = value;
        document.getElementById('registerForm').requestSubmit();
    })()`);
    await waitFor(`location.pathname === ${JSON.stringify(baseUrl.pathname + '/auth/login')} &&
        location.search.includes('registered=1') && document.readyState === 'complete' && !!document.getElementById('loginForm')`,
    'register and redirect to login');
    await evaluate(`(() => {
        document.getElementById('email').value = ${JSON.stringify(email)};
        document.getElementById('password').value = 'SmokeTest#123';
        document.getElementById('loginForm').requestSubmit();
    })()`);
    await waitFor(`location.pathname === ${JSON.stringify(baseUrl.pathname + '/customer/profile')} &&
        document.readyState === 'complete' && document.getElementById('email')?.value === ${JSON.stringify(email)}`,
    'login and load real profile');
    const customer = expectApi(await api('/api/profile'), 200);
    assert.equal(customer.email, email);
    assert.equal(customer.accountType, 'CUSTOMER');
    const cookies = await send('Network.getCookies', {urls: [base]});
    assert(cookies.cookies.some(cookie => cookie.name === 'JSESSIONID' && cookie.httpOnly), 'Missing HttpOnly session cookie.');
    passed('registration-login-and-real-session');

    const cart = expectApi(await api('/api/cart/items', 'POST',
        {foodId: 'CI_FOOD', quantity: 3, optionIds: ['CI_OPT']}), 200);
    assert.equal(cart.totalItems, 3);
    assert.equal(cart.subtotal, 135000);
    await navigate('/cart', '#cartItemsList');
    await waitFor('document.getElementById("cartItemsList").textContent.includes("CI smoke bowl")', 'load real cart');
    passed('cart-api-and-real-jsp');

    const promotion = expectApi(await api('/api/promotions/validate', 'POST',
        {code: 'CI_SMOKE_20K', subtotal: cart.subtotal}), 200);
    assert.equal(promotion.valid, true);
    assert.equal(promotion.discountAmount, 20000);
    await navigate('/checkout', '#checkoutForm');
    await waitFor('document.getElementById("placeOrderBtn")?.disabled === false', 'load checkout');
    const order = expectApi(await api('/api/orders', 'POST', {
        fulfillmentType: 'PICKUP', receiverName: 'CI Smoke Tester', receiverPhone: phone,
        promotionId: 'CI_PROMO', paymentMethod: 'CASH'
    }), 201);
    assert.equal(order.customerId, customer.id);
    assert.equal(order.subtotal, 135000);
    assert.equal(order.discountAmount, 20000);
    assert.equal(order.totalAmount, 115000);
    assert.equal(order.items.length, 1);
    assert.equal(order.items[0].quantity, 3);
    assert.equal(expectApi(await api('/api/orders/' + order.orderId), 200).orderId, order.orderId);
    assert.equal(expectApi(await api('/api/cart'), 200).totalItems, 0);
    expectApi(await api('/api/orders', 'POST', {
        fulfillmentType: 'PICKUP', receiverName: 'CI Smoke Tester', receiverPhone: phone, paymentMethod: 'CASH'
    }), 400);
    passed('voucher-checkout-persistence-and-empty-cart');

    expectApi(await api('/api/admin/orders'), 403);
    passed('customer-cannot-access-admin');
    expectApi(await api('/api/auth/logout', 'POST'), 200);
    expectApi(await api('/api/cart'), 401);
    await send('Page.navigate', {url: base + '/customer/profile'});
    await waitFor(`location.pathname === ${JSON.stringify(baseUrl.pathname + '/auth/login')} &&
        document.readyState === 'complete' && !!document.getElementById('loginForm')`, 'logged-out page redirect');
    passed('logout-invalidates-session-and-protects-jsp');
}

(async () => {
    try {
        await run();
        fs.writeFileSync(path.join(artifacts, 'result.json'), JSON.stringify({success: true, checks}, null, 2));
    } catch (error) {
        console.error(error);
        fs.writeFileSync(path.join(artifacts, 'result.json'), JSON.stringify({success: false, checks, error: error.message}, null, 2));
        if (socket?.readyState === WebSocket.OPEN) {
            try {
                fs.writeFileSync(path.join(artifacts, 'page.html'), await evaluate('document.documentElement.outerHTML'));
                const screenshot = await send('Page.captureScreenshot');
                fs.writeFileSync(path.join(artifacts, 'failure.png'), Buffer.from(screenshot.data, 'base64'));
            } catch (_) { /* Preserve the original failure if diagnostics cannot be captured. */ }
        }
        process.exitCode = 1;
    } finally {
        socket?.close();
        browser.kill();
        console.log('Runtime checks completed: ' + checks.length);
    }
})();
