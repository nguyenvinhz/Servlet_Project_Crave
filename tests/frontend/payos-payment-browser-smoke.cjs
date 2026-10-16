// Real pending order pages from tests/runtime/http-smoke.cjs. Never creates a gateway link.
const {launchBrowser, assert} = require('./browser-runtime.cjs');
(async () => {
    const email = process.env.CRAVE_SMOKE_CUSTOMER_EMAIL, password = process.env.CRAVE_SMOKE_CUSTOMER_PASSWORD;
    const cashId = process.env.CRAVE_SMOKE_CASH_ORDER_ID, onlineId = process.env.CRAVE_SMOKE_ONLINE_ORDER_ID;
    const foreignId = process.env.CRAVE_SMOKE_FOREIGN_ORDER_ID;
    assert(email && password && cashId && onlineId && foreignId,
        'Run HTTP smoke and load backend/target/runtime-customer.env first.');
    const browser = await launchBrowser();
    try {
        await browser.navigate('/auth/login');
        await browser.evaluate(`(() => {
            const form = document.getElementById('loginForm');
            form.elements.email.value = ${JSON.stringify(email)};
            form.elements.password.value = ${JSON.stringify(password)};
            form.requestSubmit();
        })()`);
        await browser.waitFor(`!location.pathname.endsWith('/auth/login') && document.readyState === 'complete'`, 'Customer login failed.');

        await browser.navigate('/orders/' + cashId);
        assert(await browser.evaluate(`document.body.textContent.includes('CASH')
            && ![...document.forms].some(form => form.action.includes('/payments/payos/'))`),
            'Cash order exposed an online payment action.');
        console.log('PASS cash order renders without a gateway action');

        for (const suffix of ['', '?payment=return', '?payment=cancel']) {
            await browser.navigate('/orders/' + onlineId + suffix);
            const result = await browser.evaluate(`(() => {
                const form = [...document.forms].find(form => form.action.endsWith('/payments/payos/' + ${JSON.stringify(onlineId)}));
                return {form:!!form, method:form?.method, action:form?.action,
                    pending:/BANK_TRANSFER\\s*·\\s*PENDING/.test(document.body.textContent),
                    refresh:[...document.querySelectorAll('a')].some(link =>
                        link.href === ${JSON.stringify(browser.base + '/orders/' + onlineId)} && link.textContent.includes('Kiểm tra thanh toán')),
                    externalImages:[...document.images].some(image => image.alt.includes('QR') && !image.src.startsWith(location.origin + '/'))};
            })()`);
            assert(result.form && result.method === 'post' && result.action === browser.base + '/payments/payos/' + onlineId,
                'Online order must render a native payment form.');
            assert(result.pending && result.refresh && !result.externalImages, 'Order return URL changed payment state or QR routing.');
        }
        console.log('PASS online payment form, status refresh and untrusted return/cancel flags');

        await browser.navigate('/orders/' + foreignId);
        assert(await browser.evaluate(`document.title.includes('403') && !document.querySelector('.order-detail-page')`),
            'A customer could open another customer order.');
        console.log('PASS order ownership rejects foreign customer order');

        browser.assertNoBrowserApi();
        assert(!browser.requests.some(request => request.method === 'POST' && request.url.includes('/payments/payos/')),
            'Browser smoke attempted to create a gateway payment link.');
        console.log('Payment browser checks passed; no gateway link or charge was created.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
