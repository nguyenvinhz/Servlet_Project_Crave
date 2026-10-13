// Run the real order-detail JSP/script in Chrome or Edge with mocked payment APIs.
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(fs.existsSync);
if (!chrome) throw new Error('Set CHROME_PATH to Chrome or Edge.');
const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-payos-browser-'));
const common = `
const calls = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const waitFor = async predicate => {
    for (let i = 0; i < 120; i++) { if (predicate()) return; await sleep(10); }
    throw new Error('Condition timed out');
};
const originalSetTimeout = window.setTimeout.bind(window);
window.setTimeout = (callback, delay, ...args) => originalSetTimeout(callback, delay === 5000 ? 80 : delay, ...args);
const order = {orderId:'DH01', orderedAt:'2026-10-13T12:00:00', receiverName:'Khách hàng', receiverPhone:'0901234567',
    fulfillmentType:'PICKUP', status:'PENDING_CONFIRMATION', subtotal:100000, totalAmount:100000,
    items:[], history:[], payment:{paymentId:'TT01', method:'BANK_TRANSFER', status:'PENDING'}};
const payment = {orderId:'DH01', paymentId:'TT01', orderCode:12345, amount:123456, currency:'VND',
    status:'PENDING', paymentStatus:'PENDING', checkoutUrl:'https://pay.payos.vn/web/example',
    qrCode:'000201-vietqr-payload', accountNumber:'0123456789', accountName:'CRAVE', description:'DH01', bin:'970422'};
const reply = (status, data, error) => ({ok:status >= 200 && status < 300, status,
    json:async () => ({success:status < 400, data, error})});
window.fetch = async (url, options = {}) => {
    const call = {path:new URL(url, location.origin).pathname, method:options.method || 'GET', options};
    calls.push(call);
    if (call.path === '/crave/api/orders/DH01') return reply(200, JSON.parse(JSON.stringify(order)));
    assert(call.path === '/crave/api/payos/orders/DH01', 'Incorrect payment endpoint/context path');
    assert(options.credentials === 'same-origin', 'Payment API omitted session credentials');
    return paymentFetch(call);
};
const paymentCalls = () => calls.filter(call => call.path.includes('/payos/'));
const statusText = () => document.getElementById('payosStatus')?.textContent || '';
const pendingReady = () => document.getElementById('payosQr') && !document.getElementById('payosQr').hidden;
`;

const cases = [
    {
        name: 'cash-paid-refunded-cancelled-do-not-create-link',
        setup: `const paymentFetch = async () => { throw new Error('Unexpected payment request'); }; order.payment.method = 'CASH';`,
        run: `
            await waitFor(() => document.querySelector('[data-payment-status]'));
            assert(!document.getElementById('payosPayment'), 'Cash order displayed QR');
            for (const state of ['SUCCESS','REFUNDED']) {
                order.payment.method = 'BANK_TRANSFER'; order.payment.status = state;
                await loadOrderDetail(); assert(!document.getElementById('payosPayment'), state + ' displayed QR');
            }
            order.payment.method = 'E_WALLET'; order.payment.status = 'PENDING'; order.status = 'CANCELLED';
            await loadOrderDetail(); assert(!document.getElementById('payosPayment'), 'Cancelled order displayed QR');
            assert(paymentCalls().length === 0, 'Ineligible order requested provider');
        `
    },
    {
        name: 'bank-qr-server-amount-safe-text-paid-stops-polling',
        setup: `
            payment.accountName = '<img src=x onerror=window.evil=1>';
            payment.description = '<svg onload=window.evil=1>';
            payment.qrCode = 'javascript:window.evil=1';
            payment.status = 'PROCESSING';
            let polls = 0;
            const paymentFetch = async call => reply(200, call.method === 'GET' && ++polls === 1
                ? {...payment, status:'PAID', paymentStatus:'SUCCESS'} : payment);
        `,
        run: `
            await waitFor(pendingReady);
            assert(paymentCalls()[0].method === 'POST', 'Initial link request was not POST');
            assert(document.getElementById('payosAmount').textContent === '123.456 đ', 'Client total replaced provider amount');
            assert(document.getElementById('payosAccountNumber').textContent === '0123456789', 'Bank account omitted');
            assert(document.getElementById('payosAccountName').textContent === payment.accountName, 'Provider text lost');
            assert(!document.querySelector('.payos-bank-details img') && !document.querySelector('#payosPayment svg') && !window.evil, 'Provider content became HTML');
            assert(new URL(document.getElementById('payosQr').src).pathname === '/crave/api/payos/orders/DH01/qr', 'Raw QR payload became an image URL');
            assert(document.getElementById('payosCheckoutLink').href === payment.checkoutUrl, 'Provider checkout omitted');
            assert(statusText().includes('Chờ thanh toán'), 'URL success query forged payment confirmation');
            await waitFor(() => statusText().includes('Đã nhận thanh toán'));
            assert(document.querySelector('[data-payment-status]').textContent === 'Đã thanh toán', 'Payment summary did not update');
            assert(document.getElementById('payosQr').hidden && document.getElementById('payosCheckoutLink').hidden, 'Paid order still accepts payment');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Paid order kept polling');
        `
    },
    {
        name: 'provider-refunded-stops-payment-and-updates-summary',
        setup: `const paymentFetch = async call => reply(200, call.method === 'GET'
            ? {...payment, status:'REFUNDED', paymentStatus:'REFUNDED'} : payment);`,
        run: `
            await waitFor(pendingReady);
            await waitFor(() => statusText().includes('đã được hoàn tiền'));
            assert(document.querySelector('[data-payment-status]').textContent === 'Đã hoàn tiền', 'Refund summary did not update');
            assert(document.getElementById('payosQr').hidden && !document.getElementById('payosQr').hasAttribute('src'), 'Refunded payment retained QR');
            assert(document.getElementById('payosCheckoutLink').hidden && !document.getElementById('payosCheckoutLink').hasAttribute('href'), 'Refunded payment retained checkout link');
            assert(document.getElementById('payosRetry').hidden && document.getElementById('payosActions').hidden, 'Refunded payment retained retry');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Refunded payment kept polling');
        `
    },
    {
        name: 'wallet-https-url-validation-and-pagehide-abort',
        setup: `
            order.payment.method = 'E_WALLET'; payment.checkoutUrl = 'javascript:window.evil=1';
            let started; const pending = new Promise(resolve => started = resolve);
            const paymentFetch = async call => {
                if (call.method === 'POST') return reply(200, payment);
                started(call); return new Promise((resolve, reject) => {
                    call.options.signal.addEventListener('abort', () => reject(new DOMException('Aborted','AbortError')), {once:true});
                });
            };
        `,
        run: `
            await waitFor(pendingReady);
            assert(document.getElementById('payosCheckoutLink').hidden && !document.getElementById('payosCheckoutLink').hasAttribute('href'), 'Unsafe checkout URL allowed');
            assert(document.getElementById('payosHint').textContent.includes('ví hỗ trợ VietQR'), 'Wallet compatibility guidance missing');
            const call = await pending;
            window.dispatchEvent(new Event('pagehide'));
            assert(call.options.signal.aborted, 'Leaving page did not abort provider request');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Pagehide left a poll running');
        `
    },
    {
        name: 'creation-error-retry-and-no-overlapping-polls',
        setup: `
            let creates = 0, active = 0, maxActive = 0;
            const paymentFetch = async call => {
                active++; maxActive = Math.max(maxActive, active);
                await sleep(30); active--;
                if (call.method === 'POST' && ++creates === 1) return reply(503, null, {message:'Chưa thể kết nối thanh toán'});
                return reply(200, payment);
            };
        `,
        run: `
            await waitFor(() => statusText().includes('Chưa thể kết nối'));
            const retry = document.getElementById('payosRetry');
            assert(!retry.hidden && !retry.disabled, 'Error offered no retry');
            retry.click(); retry.click();
            await waitFor(pendingReady);
            assert(creates === 2, 'Double retry created duplicate requests');
            retry.click(); retry.click(); await sleep(240);
            assert(maxActive === 1, 'Payment requests overlapped');
            assert(paymentCalls().filter(call => call.method === 'POST').length === 2, 'Polling recreated the link');
            window.dispatchEvent(new Event('pagehide'));
        `
    },
    {
        name: 'visibility-pauses-and-resumes-sync',
        setup: `
            let hidden = false;
            Object.defineProperty(document, 'hidden', {get:() => hidden, configurable:true});
            const paymentFetch = async () => reply(200, payment);
        `,
        run: `
            await waitFor(pendingReady);
            hidden = true; document.dispatchEvent(new Event('visibilitychange'));
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Hidden page kept polling');
            hidden = false; document.dispatchEvent(new Event('visibilitychange'));
            await waitFor(() => paymentCalls().length > count);
            assert(paymentCalls().at(-1).method === 'GET', 'Resume recreated payment link');
            window.dispatchEvent(new Event('pagehide'));
        `
    },
    {
        name: 'expired-link-stops-with-new-order-guidance',
        setup: `const paymentFetch = async () => reply(200, {...payment, status:'EXPIRED', paymentStatus:'FAILED'});`,
        run: `
            await waitFor(() => statusText().includes('hết hạn'));
            assert(document.getElementById('payosQr').hidden, 'Expired QR still shown');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Expired link kept polling');
            assert(document.getElementById('payosRetry').hidden && document.getElementById('payosActions').hidden, 'Expired payment offered an unavailable new QR');
            assert(statusText().includes('liên hệ nhà hàng hoặc đặt đơn mới'), 'Expired payment omitted recovery guidance');
            window.dispatchEvent(new Event('pagehide'));
        `
    },
    {
        name: 'provider-cancelled-link-stops-polling',
        setup: `const paymentFetch = async () => reply(200, {...payment, status:'CANCELLED', paymentStatus:'FAILED'});`,
        run: `
            await waitFor(() => statusText().includes('Giao dịch đã bị hủy'));
            assert(document.getElementById('payosQr').hidden && document.getElementById('payosCheckoutLink').hidden, 'Cancelled provider link is still payable');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Cancelled provider link kept polling');
            assert(document.getElementById('payosRetry').hidden && document.getElementById('payosActions').hidden, 'Cancelled payment offered an unavailable new QR');
            assert(statusText().includes('liên hệ nhà hàng hoặc đặt đơn mới'), 'Cancelled payment omitted recovery guidance');
            window.dispatchEvent(new Event('pagehide'));
        `
    },
    {
        name: 'checkout-url-requires-payos-https-host-port',
        setup: `const paymentFetch = async () => reply(200, payment);`,
        run: `
            await waitFor(pendingReady);
            for (const url of ['javascript:window.evil=1', 'http://pay.payos.vn/web/example',
                    'https://evil.example/web/example', 'https://pay.payos.vn.evil.example/web/example',
                    'https://pay.payos.vn:8443/web/example', 'https://user:pass@pay.payos.vn/web/example']) {
                payment.checkoutUrl = url; await loadOrderDetail(); await waitFor(pendingReady);
                const link = document.getElementById('payosCheckoutLink');
                assert(link.hidden && !link.hasAttribute('href'), 'Unsafe checkout URL allowed: ' + url);
            }
            payment.checkoutUrl = 'https://pay.payos.vn:443/web/example';
            await loadOrderDetail(); await waitFor(pendingReady);
            assert(!document.getElementById('payosCheckoutLink').hidden, 'Standard HTTPS port was rejected');
            window.dispatchEvent(new Event('pagehide'));
        `
    },
    {
        name: 'cancelling-order-disposes-payment-controller',
        setup: `const paymentFetch = async () => reply(200, payment);`,
        run: `
            await waitFor(pendingReady);
            order.status = 'CANCELLED'; await loadOrderDetail();
            assert(!document.getElementById('payosPayment'), 'Cancelled order retained QR card');
            const count = paymentCalls().length; await sleep(240);
            assert(paymentCalls().length === count, 'Cancelled order kept polling');
        `
    }
];

let current;
const server = http.createServer((req, res) => {
    const requestPath = new URL(req.url, 'http://127.0.0.1').pathname;
    const asset = /^\/crave\/assets\/(js|css)\/([\w.-]+)$/.exec(requestPath);
    if (asset) {
        res.setHeader('Content-Type', asset[1] === 'js' ? 'text/javascript; charset=utf-8' : 'text/css; charset=utf-8');
        res.end(fs.readFileSync(path.join(project, 'frontend/assets', asset[1], asset[2])));
        return;
    }
    if (requestPath.endsWith('/qr')) {
        res.setHeader('Content-Type', 'image/png');
        res.end(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1ioAAAAASUVORK5CYII=', 'base64'));
        return;
    }
    const markup = fs.readFileSync(path.join(project, 'frontend/WEB-INF/views/order/order-detail.jsp'), 'utf8')
        .replace(/<%@[^]*?%>/g, '').replace(/<c:set[^]*?\/>/g, '')
        .replace(/<c:url value='([^']*)'\s*\/>/g, (_, value) => '/crave' + value);
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(`<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/crave/assets/css/order.css"></head>
        <body class="order-page" data-context-path="/crave"><script>${common}${current.setup}</script>${markup}
        <script>document.addEventListener('DOMContentLoaded', async () => {
            const result = document.createElement('pre'); result.id = 'smoke-result';
            try { ${current.run} result.textContent = 'PASS ${current.name}'; }
            catch (error) { window.dispatchEvent(new Event('pagehide')); result.textContent = 'FAIL ' + error.message; }
            document.body.append(result);
        });</script></body></html>`);
});

async function browser(url, index) {
    return new Promise((resolve, reject) => {
        let output = '';
        const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions',
            '--no-first-run', '--user-data-dir=' + path.join(artifacts, 'profile-' + index), '--dump-dom',
            '--virtual-time-budget=4000', url], { windowsHide: true });
        child.stdout.on('data', chunk => output += chunk);
        child.stderr.resume();
        const timeout = setTimeout(() => { child.kill(); reject(new Error('Browser timed out')); }, 20000);
        child.on('error', error => { clearTimeout(timeout); reject(error); });
        child.on('close', () => { clearTimeout(timeout); resolve(output); });
    });
}

(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    let failed = 0;
    const filter = process.argv[2] ? new RegExp(process.argv[2]) : null;
    const selected = cases.filter(test => !filter || filter.test(test.name));
    for (let index = 0; index < selected.length; index++) {
        current = selected[index];
        const html = await browser(`http://127.0.0.1:${server.address().port}/crave/orders/DH01?success=true&status=PAID`, index);
        fs.writeFileSync(path.join(artifacts, current.name + '.html'), html);
        const result = [...html.matchAll(/<pre id="smoke-result">([^<]*)<\/pre>/g)].at(-1)?.[1] || 'FAIL no browser result';
        console.log(current.name + ': ' + result);
        if (!result.startsWith('PASS')) failed++;
    }
    server.close();
    console.log(`payOS browser flows: ${selected.length - failed}/${selected.length} passed. Artifacts: ${artifacts}`);
    process.exitCode = failed ? 1 : 0;
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
