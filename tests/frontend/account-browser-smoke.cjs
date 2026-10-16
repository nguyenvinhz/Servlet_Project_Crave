// Node 22+; start the packaged WAR first. Optional disposable customer credentials:
// CRAVE_SMOKE_CUSTOMER_EMAIL and CRAVE_SMOKE_CUSTOMER_PASSWORD.
const {launchBrowser, assert} = require('./browser-runtime.cjs');

(async () => {
    const browser = await launchBrowser();
    let passed = 0;
    try {
        await browser.navigate('/auth/login');
        const login = await browser.evaluate(`(() => {
            const form = document.getElementById('loginForm');
            let submits = 0;
            form.addEventListener('submit', () => { submits++; });
            form.requestSubmit();
            return {action:form.action, method:form.method, valid:form.checkValidity(), submits,
                password:form.elements.password.value};
        })()`);
        assert(login.action === browser.base + '/auth/login' && login.method === 'post', 'Login must use a native form POST.');
        assert(!login.valid && login.submits === 0 && login.password === '', 'Native validation did not block the empty login.');
        console.log('PASS login native form and validation'); passed++;

        await browser.evaluate(`(() => {
            const form = document.getElementById('loginForm');
            form.elements.email.value = 'absent-smoke-account@example.invalid';
            form.elements.password.value = 'invalidPassword123';
            form.requestSubmit();
        })()`);
        await browser.waitFor(`location.pathname.endsWith('/auth/login') && !!document.getElementById('form-error')?.textContent.trim()`,
            'Invalid login did not render a server form error.');
        assert(await browser.evaluate(`document.getElementById('password').value === ''`), 'Password was echoed into the response.');
        console.log('PASS invalid login returns an HTML error'); passed++;

        for (const route of ['/auth/register', '/auth/forgot-password']) {
            await browser.navigate(route);
            assert(await browser.evaluate(`[...document.forms].some(form =>
                form.method === 'post' && new URL(form.getAttribute('action'), location.origin).pathname === location.pathname)`), 'Missing account form at ' + route);
        }
        console.log('PASS registration and recovery pages render'); passed++;

        const email = process.env.CRAVE_SMOKE_CUSTOMER_EMAIL;
        const password = process.env.CRAVE_SMOKE_CUSTOMER_PASSWORD;
        if (email && password) {
            await browser.navigate('/auth/login');
            await browser.evaluate(`(() => {
                const form = document.getElementById('loginForm');
                form.elements.email.value = ${JSON.stringify(email)};
                form.elements.password.value = ${JSON.stringify(password)};
                form.elements.returnTo.value = '//invalid.example/redirect';
                form.requestSubmit();
            })()`);
            await browser.waitFor(`location.href === ${JSON.stringify(browser.base + '/menu')} && document.readyState === 'complete'`,
                'Valid login did not redirect safely to the menu.');
            await browser.navigate('/customer/profile');
            assert(await browser.evaluate(`(() => {
                const form = document.getElementById('profileForm');
                return form.method === 'post' && form.elements.email.value === ${JSON.stringify(email)}
                    && !!form.elements.fullName.value && !form.querySelector('fieldset').disabled;
            })()`), 'Customer profile was not populated by the server.');
            await browser.evaluate(`document.getElementById('profileForm').requestSubmit()`);
            await browser.waitFor(`document.getElementById('form-success')?.textContent.includes('Đã cập nhật hồ sơ.')`,
                'Profile save did not redirect to its success message.');
            console.log('PASS login, profile load and save'); passed++;

            const marker = 'Native browser smoke ' + Date.now();
            await browser.navigate('/customer/addresses');
            await browser.evaluate(`(() => {
                const form = document.getElementById('addressForm');
                form.elements.addressLine.value = '12 Nguyễn Trãi, Thành phố Hồ Chí Minh';
                form.elements.note.value = ${JSON.stringify(marker)};
                form.requestSubmit();
            })()`);
            await browser.waitFor(`[...document.querySelectorAll('.address-item')].some(item => item.textContent.includes(${JSON.stringify(marker)}))`,
                'Address creation did not render the saved address.');
            const edit = await browser.evaluate(`[...document.querySelectorAll('.address-item')].find(item =>
                item.textContent.includes(${JSON.stringify(marker)})).querySelector('a').href`);
            await browser.navigate(edit.substring(browser.base.length));
            assert(await browser.evaluate(`document.getElementById('note').value === ${JSON.stringify(marker)}`),
                'Address edit did not load the selected address.');
            await browser.evaluate(`(() => {
                const form = document.getElementById('addressForm');
                form.elements.note.value = ${JSON.stringify(marker + ' updated')};
                form.requestSubmit();
            })()`);
            await browser.waitFor(`[...document.querySelectorAll('.address-item')].some(item => item.textContent.includes(${JSON.stringify(marker + ' updated')}))`,
                'Address update did not render the edited note.');
            await browser.evaluate(`(() => {
                window.confirm = () => true;
                const item = [...document.querySelectorAll('.address-item')].find(item =>
                    item.textContent.includes(${JSON.stringify(marker + ' updated')}));
                item.querySelector('button[value="delete"]').click();
            })()`);
            await browser.waitFor(`document.getElementById('form-success')?.textContent.includes('Đã xóa địa chỉ.')
                && ![...document.querySelectorAll('.address-item')].some(item => item.textContent.includes(${JSON.stringify(marker)}))`,
                'Address delete did not render the refreshed list.').catch(async error => {
                    const state = await browser.evaluate(`({path:location.pathname,
                        success:document.getElementById('form-success')?.textContent.trim(),
                        error:document.getElementById('form-error')?.textContent.trim(),
                        remaining:[...document.querySelectorAll('.address-item')].filter(item => item.textContent.includes(${JSON.stringify(marker)})).length})`);
                    throw new Error(error.message + ' ' + JSON.stringify(state));
                });
            console.log('PASS native address create, edit and delete'); passed++;

            await browser.evaluate(`document.getElementById('logoutForm').requestSubmit()`);
            await browser.waitFor(`location.pathname.endsWith('/auth/login') && !!document.getElementById('loginForm')`,
                'Logout did not return to the login page.');
            console.log('PASS native logout'); passed++;
        } else {
            console.log('SKIP authenticated browser cases: provide disposable CRAVE_SMOKE_CUSTOMER_EMAIL/PASSWORD.');
        }
        browser.assertNoBrowserApi();
        console.log('Account browser checks: ' + passed + ' passed; no browser API or JSON requests.');
    } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
