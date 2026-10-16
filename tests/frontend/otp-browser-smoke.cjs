// Checks real server-rendered OTP forms without sending email.
const {launchBrowser, assert} = require('./browser-runtime.cjs');
(async () => {
    const browser = await launchBrowser();
    let passed = 0;
    try {
        for (const route of ['/auth/register', '/auth/forgot-password']) {
            await browser.navigate(route);
            const form = await browser.evaluate(`(() => {
                const code = document.querySelector('input[name="verificationCode"]');
                const form = code.form;
                const send = form.querySelector('button[name="action"][value="sendCode"]');
                return {method:form.method, action:new URL(form.getAttribute('action'), location.origin).href, enabled:!code.disabled, pattern:code.pattern,
                    required:code.required, resend:!!send && send.formNoValidate,
                    verify:[...form.querySelectorAll('button[type="submit"]')].some(button => button !== send && !button.formNoValidate),
                    hiddenPasswords:[...form.querySelectorAll('input[type="hidden"]')].some(input => /password/i.test(input.name))};
            })()`);
            assert(form.method === 'post' && form.action === browser.base + route, 'OTP must submit to its Servlet route.');
            assert(form.enabled && form.required && form.pattern === '[0-9]{6}', 'OTP code must work without JavaScript initialization.');
            assert(form.resend && form.verify && !form.hiddenPasswords, 'Missing native send/verify actions or password stored in hidden input.');
            console.log('PASS native OTP form ' + route); passed++;
        }
        browser.assertNoBrowserApi();
        console.log('OTP browser checks: ' + passed + ' passed; no email was sent.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
