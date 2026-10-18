// Native Servlet/JSP HTTP checks against a loopback test server.
const assert = require('node:assert/strict');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8083/crave').replace(/\/$/, '');
const origin = new URL(base).origin;
assert(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname), 'HTTP smoke must use loopback.');
let checks = 0;
class Client {
    cookie = '';
    async request(path, {method = 'GET', fields, status = 200, source = origin} = {}) {
        const headers = {Accept:'text/html'};
        if (this.cookie) headers.Cookie = this.cookie;
        if (method !== 'GET') headers.Origin = source;
        if (fields) headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
        const response = await fetch(base + path, {method, headers, redirect:'manual',
            body:fields ? new URLSearchParams(fields) : undefined});
        const cookies = response.headers.getSetCookie();
        if (cookies.length) this.cookie = cookies.map(value => value.split(';')[0]).join('; ');
        const text = await response.text();
        assert.equal(response.status, status, method + ' ' + path + ': unexpected status ' + response.status);
        if (status === 200 && !path.includes('health')) {
            assert.match(response.headers.get('content-type') || '', /text\/html/i);
            assert.equal((text.match(/<html\b/gi) || []).length, 1, path + ': duplicate HTML document');
            assert.equal((text.match(/<body\b/gi) || []).length, 1, path + ': duplicate body');
            assert(!text.includes('HTTP Status 500'), path + ': JSP error');
        }
        checks++;
        return {response, text};
    }
}
(async () => {
    const guest = new Client();
    const health = await guest.request('/health');
    assert.equal(health.response.headers.get('content-type').split(';')[0], 'text/plain');
    assert.equal(health.text.trim(), 'application=UP\ndatabase=UP');
    for (const path of ['/menu', '/promotions', '/auth/login', '/auth/register', '/auth/forgot-password'])
        await guest.request(path);
    const search = await guest.request('/menu?keyword=' + encodeURIComponent('<script>window.smoke=1</script>'));
    assert(!search.text.includes('<script>window.smoke=1</script>'), 'Search text must be escaped.');
    for (const path of ['/cart', '/checkout', '/orders', '/customer/profile', '/customer/addresses', '/admin']) {
        const result = await guest.request(path, {status:302});
        assert.match(result.response.headers.get('location'), /\/auth\/login\?returnTo=/);
    }
    await guest.request('/cart/action', {method:'POST', fields:{action:'clear'}, source:'https://foreign.invalid', status:403});
    const invalid = await guest.request('/auth/login/submit', {method:'POST',
        fields:{email:'nobody-' + Date.now() + '@example.test',password:'WrongPassword123'},status:401});
    assert(invalid.text.includes('loginForm'), 'Failed login must render the existing login form.');
    for (const path of ['/api/cart', '/api/foods', '/api/auth/session']) {
        const response = await fetch(base + path, {redirect:'manual'});
        assert(!String(response.headers.get('content-type')).includes('application/json'), 'Obsolete API still emits JSON.');
        checks++;
    }
    const email = process.env.CRAVE_SMOKE_ADMIN_EMAIL, password = process.env.CRAVE_SMOKE_ADMIN_PASSWORD;
    if (email && password) {
        const admin = new Client();
        const login = await admin.request('/auth/login/submit', {method:'POST',fields:{email,password},status:302});
        assert.match(login.response.headers.get('location'), /\/admin/);
        assert(admin.cookie.includes('JSESSIONID='), 'Login must establish a servlet session.');
        for (const path of ['/admin','/admin/menu','/admin/categories','/admin/orders','/admin/customers','/admin/employees','/admin/promotions','/admin/reports'])
            await admin.request(path);
        await admin.request('/customer/addresses', {status:403});
        await admin.request('/admin/categories/action', {method:'POST',fields:{action:'create',name:'forbidden'},source:'https://foreign.invalid',status:403});
        await admin.request('/auth/logout', {method:'POST',fields:{},status:302});
        await admin.request('/admin', {status:302});
    }
    console.log('Servlet/JSP HTTP smoke: ' + checks + ' checks passed.');
})().catch(error => { console.error(error.message); process.exitCode = 1; });