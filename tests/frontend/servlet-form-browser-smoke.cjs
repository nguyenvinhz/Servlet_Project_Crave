// Actual Servlet/JSP form smoke. Requires a disposable database and local SMTP fixture.
// Covers account, menu, cart, pickup cash checkout, and order cancellation.
// CRAVE_SMOKE_BASE_URL defaults to http://127.0.0.1:8083/crave.
// CRAVE_SMOKE_INBOX_URL defaults to http://127.0.0.1:2526.
// Uses only Node 22+ built-ins and Chrome/Edge; does not mock application routes.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const {randomBytes, randomInt} = require('node:crypto');
const project = path.resolve(__dirname, '../..');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8083/crave').replace(/\/$/, '');
const inbox = (process.env.CRAVE_SMOKE_INBOX_URL || 'http://127.0.0.1:2526').replace(/\/$/, '');
for (const address of [base, inbox]) assert(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(address).hostname), 'Form smoke must use loopback.');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(file => fs.existsSync(file));
assert(chrome, 'Set CHROME_PATH to Chrome or Edge.');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-servlet-form-browser-'));
const artifactDirectory = path.join(project, '.runtime', 'form-browser');
fs.mkdirSync(artifactDirectory, {recursive: true});
for (const artifact of ['failure.png', 'failure.json']) fs.rmSync(path.join(artifactDirectory, artifact), {force: true});
const email = 'form-smoke-' + Date.now().toString(36) + randomBytes(3).toString('hex') + '@example.test';
const phone = '09' + String(randomInt(100000000)).padStart(8, '0');
const passwords = Array.from({length: 3}, () => randomBytes(14).toString('base64url'));
fs.writeFileSync(path.join(artifactDirectory, 'disposable-account.txt'), email);
fs.writeFileSync(path.join(artifactDirectory, 'disposable-category.txt'), '');
const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
let browser, socket, loadCount = 0, checks = 0, nextId = 1;
const pending = new Map(), scriptErrors = [], jsonRequests = [], legacyApiRequests = [];
let sessionId;
const say = message => process.stdout.write('PASS ' + message + '\n');
const send = (method, params = {}, targetSession = sessionId) => new Promise((resolve, reject) => {
    const id = nextId++;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP command timed out: ' + method)); }, 20000);
    pending.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method, params, ...(targetSession ? {sessionId: targetSession} : {})}));
});
async function evaluate(expression) {
    const reply = await send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (reply.exceptionDetails) throw new Error('Browser evaluation failed: ' + (reply.exceptionDetails.text || 'Exception'));
    return reply.result.value;
}
async function waitFor(expression, message) {
    for (let attempt = 0; attempt < 150; attempt++) {
        try { if (await evaluate(expression)) { checks++; return; } } catch (error) {
            if (!/context|navigat|Cannot find|Inspected target/i.test(error.message)) throw error;
        }
        await sleep(100);
    }
    throw new Error(message);
}
async function waitLoad(previous) {
    for (let attempt = 0; attempt < 200 && loadCount === previous; attempt++) await sleep(100);
    assert(loadCount > previous, 'Expected a normal document navigation.');
    await waitFor('document.readyState === "complete"', 'Page did not finish loading.');
    assert(!(await evaluate('document.body.innerText.includes("HTTP Status 500")')), 'JSP compilation/runtime failure.');
}
async function navigate(route) {
    const previous = loadCount;
    await send('Page.navigate', {url: base + route});
    await waitLoad(previous);
}
async function fields(values) {
    await evaluate('(' + (data => {
        Object.entries(data).forEach(([id, value]) => {
            const control = document.getElementById(id);
            if (!control) throw new Error('Missing field ' + id);
            if (control.type === 'checkbox') control.checked = Boolean(value);
            else control.value = value;
            control.dispatchEvent(new Event('input', {bubbles: true}));
        });
    }).toString() + ')(' + JSON.stringify(values) + ')');
}
async function submit(formId) {
    const previous = loadCount;
    await evaluate('document.getElementById(' + JSON.stringify(formId) + ').requestSubmit()');
    await waitLoad(previous);
}
async function click(expression) {
    const previous = loadCount;
    await evaluate(expression + '.click()');
    await waitLoad(previous);
}
async function verificationCode(purpose) {
    for (let attempt = 0; attempt < 50; attempt++) {
        const response = await fetch(inbox + '/messages?email=' + encodeURIComponent(email) + '&purpose=' + purpose);
        if (response.ok) {
            const message = await response.json();
            assert.match(message.code, /^[0-9]{6}$/);
            return message.code;
        }
        assert.equal(response.status, 404, 'Local SMTP inbox rejected the lookup.');
        await sleep(100);
    }
    throw new Error('Local SMTP inbox did not receive the verification message.');
}
async function login(password) {
    await fields({email, password});
    await submit('loginForm');
    await waitFor('location.pathname.endsWith("/menu")', 'Login did not reach the menu.');
}
async function screenshot(name) {
    const result = await send('Page.captureScreenshot', {format: 'png', captureBeyondViewport: false});
    fs.writeFileSync(path.join(artifactDirectory, name + '.png'), Buffer.from(result.data, 'base64'));
}
(async () => {
    for (const css of ['styles.css', 'account.css']) {
        const response = await fetch(base + '/assets/css/' + css);
        assert.equal(response.status, 200, 'CSS asset unavailable.');
        assert.deepEqual(Buffer.from(await response.arrayBuffer()), fs.readFileSync(path.join(project, 'src/main/webapp/assets/css', css)), 'Packaged CSS differs from the project.');
    }
    const debuggingUrl = await new Promise((resolve, reject) => {
        browser = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions',
            '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0',
            '--user-data-dir=' + work, 'about:blank'], {windowsHide: true, stdio: ['ignore', 'ignore', 'pipe']});
        let stderr = '';
        const timeout = setTimeout(() => reject(new Error('Browser did not start.')), 20000);
        browser.stderr.on('data', data => {
            stderr += data;
            const match = stderr.match(/DevTools listening on (ws:\/\/[^\s]+)/);
            if (match) { clearTimeout(timeout); resolve(match[1]); }
        });
        browser.on('error', reject);
        browser.on('exit', code => { if (!socket) reject(new Error('Browser exited before CDP connected: ' + code)); });
    });
    socket = new WebSocket(debuggingUrl);
    await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id) {
            const call = pending.get(message.id);
            if (!call) return;
            pending.delete(message.id); clearTimeout(call.timer);
            if (message.error) call.reject(new Error(message.error.message)); else call.resolve(message.result);
        } else if (message.method === 'Page.loadEventFired') loadCount++;
        else if (message.method === 'Page.javascriptDialogOpening') send('Page.handleJavaScriptDialog', {accept: true}).catch(() => {});
        else if (message.method === 'Runtime.exceptionThrown') {
            const error = message.params.exceptionDetails;
            scriptErrors.push(error.exception?.description || error.text);
        }
        else if (message.method === 'Network.requestWillBeSent') {
            const request = message.params.request;
            const contentType = Object.entries(request.headers).find(([name]) => name.toLowerCase() === 'content-type')?.[1] || '';
            if (request.url.startsWith(base) && /application\/json/i.test(contentType)) jsonRequests.push(new URL(request.url).pathname);
            if (request.url.startsWith(base + '/api/')) legacyApiRequests.push(new URL(request.url).pathname);
        }
    };
    const target = await send('Target.createTarget', {url: 'about:blank'}, null);
    sessionId = (await send('Target.attachToTarget', {targetId: target.targetId, flatten: true}, null)).sessionId;
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Emulation.setDeviceMetricsOverride', {width: 1365, height: 1000, deviceScaleFactor: 1, mobile: false});

    await navigate('/auth/login');
    await screenshot('login-desktop');
    await navigate('/auth/register');
    await screenshot('register-desktop');
    assert.equal(await evaluate('document.getElementById("otp-fields").hidden'), true);
    await fields({fullName: 'Khách kiểm thử biểu mẫu', email, phone, password: passwords[0], confirmPassword: 'mismatch123'});
    await evaluate('document.getElementById("registerForm").requestSubmit()');
    await sleep(150);
    assert.equal(await evaluate('document.getElementById("confirmPassword").validity.customError'), true, 'Password confirmation did not block submission.');
    await fields({confirmPassword: passwords[0]});
    await submit('registerForm');
    assert.equal(await evaluate('document.getElementById("otp-fields").hidden'), false, 'OTP step did not render.');
    assert.equal(await evaluate('document.getElementById("password").value'), '', 'Password was returned in the OTP document.');
    await fields({password: passwords[0], confirmPassword: passwords[0], verificationCode: await verificationCode('REGISTER')});
    await submit('registerForm');
    assert.equal(await evaluate('new URLSearchParams(location.search).get("registered")'), '1');
    say('registration and SMTP verification use normal forms');

    await fields({email, password: 'wrongPassword123'});
    await submit('loginForm');
    assert.equal(await evaluate('document.getElementById("form-error").hidden'), false, 'Invalid credentials did not render an alert.');
    await login(passwords[0]);
    await navigate('/customer/profile');
    assert.equal(await evaluate('document.getElementById("email").value'), email);
    await fields({fullName: 'Hồ sơ Servlet kiểm thử'});
    await submit('profileForm');
    assert.equal(await evaluate('document.getElementById("fullName").value'), 'Hồ sơ Servlet kiểm thử');
    await screenshot('profile-desktop');
    say('login, Vietnamese profile, and profile save render from Servlet attributes');

    await navigate('/customer/addresses');
    assert.equal(await evaluate('document.querySelectorAll(".address-item").length'), 0);
    const first = '<img src=x onerror=window.formSmokeUnsafe=1> 12 Nguyễn Trãi';
    await fields({addressLine: first, note: 'Cổng trước', isDefault: true});
    await submit('addressForm');
    assert.equal(await evaluate('document.querySelectorAll(".address-item").length'), 1);
    assert.equal(await evaluate('document.querySelector(".address-item h3").textContent'), first);
    assert.equal(await evaluate('Boolean(window.formSmokeUnsafe || document.querySelector("#address-list img"))'), false);
    await fields({addressLine: '34 Nguyễn Huệ', note: 'Tầng 2', isDefault: false});
    await submit('addressForm');
    assert.equal(await evaluate('document.querySelectorAll(".address-item").length'), 2);
    await click('[...document.querySelectorAll(".address-item")].find(item => item.querySelector("h3").textContent === "34 Nguyễn Huệ").querySelector("form[action$=\'/default\'] button")');
    assert.equal(await evaluate('[...document.querySelectorAll(".address-item")].find(item => item.querySelector("h3").textContent === "34 Nguyễn Huệ").querySelector(".address-badge").textContent'), 'Mặc định');
    await click('[...document.querySelectorAll(".address-item")].find(item => item.querySelector("h3").textContent === "34 Nguyễn Huệ").querySelector("button[data-location]")');
    assert.equal(await evaluate('document.getElementById("addressLine").value'), '34 Nguyễn Huệ');
    await fields({addressLine: '36 Nguyễn Huệ'});
    await submit('addressForm');
    assert.equal(await evaluate('[...document.querySelectorAll(".address-item h3")].some(item => item.textContent === "36 Nguyễn Huệ")'), true);
    await screenshot('addresses-desktop');
    await send('Emulation.setDeviceMetricsOverride', {width: 390, height: 844, deviceScaleFactor: 1, mobile: true});
    await screenshot('addresses-mobile');
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth + 1'), false, 'Mobile address layout overflows.');
    await send('Emulation.setDeviceMetricsOverride', {width: 1365, height: 1000, deviceScaleFactor: 1, mobile: false});
    while (await evaluate('document.querySelectorAll(".address-item").length')) {
        await click('document.querySelector(".address-item form[action$=\'/delete\'] button")');
    }
    assert.equal(await evaluate('document.getElementById("address-empty-state").hidden'), false);
    say('address create, edit, default, delete, escaped text and mobile layout');

    await navigate('/menu');
    const foodLink = await evaluate('document.querySelector(".clean-food-card:not(.food-card-unavailable) .clean-card-title a")?.getAttribute("href")');
    assert(foodLink, 'A disposable test database needs at least one available food.');
    await screenshot('menu-desktop');
    const search = '<img src=x onerror=window.formSmokeUnsafe=1>';
    await navigate('/menu?keyword=' + encodeURIComponent(search));
    assert.equal(await evaluate('document.querySelector("input[name=keyword]").value'), search);
    assert.equal(await evaluate('Boolean(window.formSmokeUnsafe || document.querySelector(".search-results-label img"))'), false);
    await navigate(new URL(foodLink, base).pathname.replace(new URL(base).pathname, '') + new URL(foodLink, base).search);
    await fields({orderQuantity: '2'});
    await submit('orderCustomizationForm');
    await navigate('/cart');
    assert.equal(await evaluate('document.querySelectorAll(".cart-item-card").length'), 1);
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent'), '2');
    await click('document.querySelector(".btn-inc")');
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent'), '3');
    await click('document.querySelector(".btn-edit")');
    assert.equal(await evaluate('document.getElementById("cartItemEditor").open'), true);
    const itemNote = '<img src=x onerror=window.formSmokeUnsafe=1> ít cay';
    await fields({cartEditorQuantity: '1', cartEditorNote: itemNote});
    await submit('cartItemEditorForm');
    assert.equal(await evaluate('document.querySelector(".cart-stepper-val").textContent'), '1');
    assert.equal(await evaluate('document.querySelector(".cart-item-note").textContent.includes(' + JSON.stringify(itemNote) + ')'), true);
    assert.equal(await evaluate('Boolean(window.formSmokeUnsafe || document.querySelector(".cart-item-note img"))'), false);
    await fields({voucherCodeInput: 'FORM-SMOKE-INVALID-' + Date.now()});
    await click('document.getElementById("voucherApplyBtn")');
    assert(await evaluate('document.getElementById("voucherFeedback").textContent.trim().length > 0'), 'Invalid voucher needs feedback.');
    await screenshot('cart-desktop');
    say('menu escaped search, food customization, cart quantity/edit and invalid voucher use form navigation');

    await click('document.getElementById("proceedCheckoutBtn")');
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), true, 'Delivery needs a confirmed location.');
    await fields({fulfillmentType: 'PICKUP', receiverName: 'Khách kiểm thử biểu mẫu', receiverPhone: phone, paymentMethod: 'CASH'});
    await evaluate('document.getElementById("fulfillmentType").dispatchEvent(new Event("change", {bubbles:true}))');
    assert.equal(await evaluate('document.getElementById("placeOrderBtn").disabled'), false);
    assert.equal(await evaluate('document.getElementById("addressGroup").hidden'), true);
    await screenshot('checkout-desktop');
    await submit('checkoutForm');
    assert.match(await evaluate('location.pathname'), /\/orders\/[^/]+$/);
    assert.equal(await evaluate('document.querySelector("[data-order-status]").dataset.orderStatus'), 'PENDING_CONFIRMATION');
    assert.equal(await evaluate('document.querySelector("[data-order-status]").dataset.fulfillment'), 'PICKUP');
    await screenshot('order-detail-desktop');
    await evaluate('document.querySelector("[data-cancel-order]").click()');
    await waitFor('Boolean(document.getElementById("btnConfirmCancel"))', 'Order cancel dialog did not open.');
    await click('document.getElementById("btnConfirmCancel")');
    assert.equal(await evaluate('document.querySelector("[data-cancel-order]")'), null, 'Cancelled order should not offer cancellation.');
    assert(await evaluate('document.getElementById("orderContent").innerText.includes("Đơn hàng đã bị hủy")'), 'Cancelled order did not render its status.');
    assert.equal(await evaluate('document.querySelector(".order-status-badge").textContent.trim()'), 'Đã hủy', 'Order status label lost Vietnamese encoding.');
    assert.equal(await evaluate('document.querySelector("[data-payment-status]").textContent.trim()'), 'Thanh toán thất bại', 'Payment status label lost Vietnamese encoding.');
    await navigate('/orders');
    assert.equal(await evaluate('document.querySelectorAll(".order-card").length'), 1);
    await navigate('/cart');
    assert.equal(await evaluate('getComputedStyle(document.getElementById("cartEmptyState")).display'), 'block');
    say('pickup cash checkout, persisted order detail/list, cancellation and cleared cart');

    await submit('logoutForm');
    await navigate('/auth/forgot-password');
    await fields({email});
    await submit('resetPasswordForm');
    await fields({verificationCode: await verificationCode('PASSWORD_RESET'), password: passwords[1], confirmPassword: passwords[1]});
    await submit('resetPasswordForm');
    assert.equal(await evaluate('new URLSearchParams(location.search).get("passwordReset")'), '1');
    await login(passwords[1]);
    await navigate('/auth/change-password');
    await fields({currentPassword: passwords[1]});
    await submit('changePasswordForm');
    await fields({verificationCode: await verificationCode('PASSWORD_CHANGE'), password: passwords[2], confirmPassword: passwords[2]});
    await submit('changePasswordForm');
    assert.equal(await evaluate('new URLSearchParams(location.search).get("passwordChanged")'), '1');
    await login(passwords[2]);
    await navigate('/customer/profile');
    await submit('logoutForm');
    assert.equal(await evaluate('new URLSearchParams(location.search).get("loggedOut")'), '1');
    say('password reset, password change, session renewal and logout');

    if (process.env.CRAVE_SMOKE_ADMIN_EMAIL && process.env.CRAVE_SMOKE_ADMIN_PASSWORD) {
        await fields({email: process.env.CRAVE_SMOKE_ADMIN_EMAIL, password: process.env.CRAVE_SMOKE_ADMIN_PASSWORD});
        await submit('loginForm');
        assert.equal(await evaluate('document.body.dataset.role'), 'ADMIN');
        for (const route of ['/admin', '/admin/orders', '/admin/menu', '/admin/categories', '/admin/customers', '/admin/employees', '/admin/promotions', '/admin/reports']) {
            await navigate(route);
            assert.equal(await evaluate('location.pathname'), new URL(base).pathname + route, 'Admin route redirected: ' + route);
            assert.equal(await evaluate('document.body.dataset.role'), 'ADMIN');
            assert.equal(await evaluate('Boolean(document.querySelector(".admin-content"))'), true, 'Admin page did not render: ' + route);
        }
        await navigate('/admin/categories');
        await evaluate('openCreateCategoryModal()');
        const categoryName = 'Biểu mẫu ' + Date.now();
        await fields({catNameInput: categoryName, catDescInput: '<img src=x onerror=window.formSmokeUnsafe=1>'});
        await submit('categoryForm');
        const categoryId = await evaluate('[...document.querySelectorAll("#adminCategoryTable tbody tr")].find(row => row.querySelector("strong")?.textContent === ' + JSON.stringify(categoryName) + ')?.id');
        assert(categoryId, 'Category was not persisted by the admin form.');
        fs.writeFileSync(path.join(artifactDirectory, 'disposable-category.txt'), categoryId.replace(/^catRow-/, ''));
        assert.equal(await evaluate('Boolean(window.formSmokeUnsafe || document.querySelector("#' + categoryId + ' img"))'), false);
        await evaluate('document.querySelector("#' + categoryId + ' .edit-btn").click()');
        await fields({catNameInput: categoryName + ' đã sửa'});
        await submit('categoryForm');
        assert.equal(await evaluate('document.querySelector("#' + categoryId + ' strong").textContent'), categoryName + ' đã sửa');
        await screenshot('admin-categories-desktop');
        await click('document.querySelector("#' + categoryId + ' .delete-btn")');
        assert.equal(await evaluate('document.getElementById(' + JSON.stringify(categoryId) + ')'), null);
        await navigate('/admin/customers');
        const customerSelector = '[data-account][data-email="' + email + '"]';
        await evaluate('document.querySelector(' + JSON.stringify(customerSelector + ' [data-status-change]') + ').click()');
        await submit('managementConfirmForm');
        assert.equal(await evaluate('document.querySelector(' + JSON.stringify(customerSelector) + ').dataset.status'), 'LOCKED');
        await evaluate('document.querySelector(' + JSON.stringify(customerSelector + ' [data-status-change]') + ').click()');
        await submit('managementConfirmForm');
        assert.equal(await evaluate('document.querySelector(' + JSON.stringify(customerSelector) + ').dataset.status'), 'ACTIVE');
        await screenshot('admin-customers-desktop');
        await click('document.getElementById("adminLogout")');
        say('disposable ADMIN login, eight admin pages, category create/edit/delete and customer lock/unlock');
    }

    assert.deepEqual(jsonRequests, [], 'Application UI sent application/json requests.');
    assert.deepEqual(legacyApiRequests, [], 'Application UI still depends on a legacy API route.');
    assert.deepEqual(scriptErrors, [], 'Uncaught browser JavaScript exceptions.');
    say('CSS assets unchanged, no application JSON requests, no uncaught JavaScript errors');
    process.stdout.write('Completed ' + checks + ' document checks; screenshots: .runtime/form-browser\n');
})().catch(async error => {
    if (sessionId && socket?.readyState === WebSocket.OPEN) {
        try {
            await screenshot('failure');
            const diagnostic = await evaluate('({url:location.pathname+location.search, title:document.title, body:document.body.innerText.slice(0,12000), invalid:[...document.querySelectorAll(":invalid")].map(field=>({id:field.id,message:field.validationMessage}))})');
            fs.writeFileSync(path.join(artifactDirectory, 'failure.json'), JSON.stringify(diagnostic, null, 2));
        } catch {}
    }
    process.stderr.write('FAIL ' + error.message + '\n'); process.exitCode = 1;
})
.finally(async () => {
    if (socket?.readyState === WebSocket.OPEN) {
        try { await send('Browser.close', {}, null); } catch {}
        socket.close();
    }
    browser?.kill();
    await sleep(250);
    const resolved = path.resolve(work), prefix = path.resolve(os.tmpdir()) + path.sep;
    if (resolved.startsWith(prefix) && path.basename(resolved).startsWith('crave-servlet-form-browser-')) {
        try { fs.rmSync(resolved, {recursive: true, force: true, maxRetries: 5, retryDelay: 100}); } catch {}
    }
});
