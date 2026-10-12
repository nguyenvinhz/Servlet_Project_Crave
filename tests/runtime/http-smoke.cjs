// Run against a disposable seeded database. This creates accounts, menu items, promotions and orders.
// CRAVE_SMOKE_ADMIN_EMAIL / CRAVE_SMOKE_ADMIN_PASSWORD identify an existing admin in that database.
const assert = require('node:assert/strict');
const {randomBytes} = require('node:crypto');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8081/crave').replace(/\/$/, '');
const origin = new URL(base).origin;
const apiOnly = process.argv.includes('--api-only');
const token = Date.now().toString(36) + randomBytes(2).toString('hex');
const password = randomBytes(14).toString('base64url');
const phone = () => '09' + String(Math.floor(Math.random() * 100000000)).padStart(8, '0');
let checks = 0;

class Client {
    cookie = '';
    async request(path, {method = 'GET', body, status = 200} = {}) {
        const headers = {Accept: 'application/json', Origin: origin};
        if (this.cookie) headers.Cookie = this.cookie;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        const response = await fetch(base + path, {method, headers, redirect: 'manual',
            body: body === undefined ? undefined : JSON.stringify(body)});
        const cookies = response.headers.getSetCookie();
        if (cookies.length) this.cookie = cookies.map(cookie => cookie.split(';')[0]).join('; ');
        const text = await response.text();
        assert.equal(response.status, status, `${method} ${path}: ${text.replace(/<[^>]+>/g, ' ').slice(0, 600)}`);
        checks++;
        if (path.startsWith('/api/')) {
            if (status === 204) return null;
            const json = JSON.parse(text);
            assert.equal(json.success, status < 400, `${method} ${path}: invalid API envelope`);
            if (status < 400) assert(!/passwordHash|password_hash/.test(text), `Leaked password hash at ${path}`);
            return json.data;
        }
        if (status === 200) {
            assert.equal((text.match(/<html\b/gi) || []).length, 1, `${path}: duplicate HTML document`);
            assert.equal((text.match(/<body\b/gi) || []).length, 1, `${path}: duplicate body`);
            assert(!text.includes('HTTP Status 500'), `${path}: JSP exception`);
            assert(/charset\s*=\s*["']?UTF-8/i.test(text), `${path}: missing UTF-8 metadata`);
        }
        return text;
    }
    login(email, userPassword) {
        return this.request('/api/auth/login', {method: 'POST', body: {email, password: userPassword}});
    }
}

(async () => {
    const guest = new Client();
    const customer = new Client();
    const admin = new Client();
    assert(process.env.CRAVE_SMOKE_ADMIN_EMAIL && process.env.CRAVE_SMOKE_ADMIN_PASSWORD,
        'Set CRAVE_SMOKE_ADMIN_EMAIL and CRAVE_SMOKE_ADMIN_PASSWORD for the test database.');
    const health = await guest.request('/api/health');
    assert.equal(health.database, 'UP');
    for (const page of ['/menu', '/menu/detail?id=MA02', '/promotions', '/auth/login', '/auth/register']) {
        await guest.request(page);
    }
    await guest.request('/api/admin/customers', {status: 401});
    await guest.request('/api/cart', {status: 401});
    await guest.request('/admin', {status: 302});
    const account = await guest.request('/api/auth/register', {method: 'POST', status: 201,
        body: {fullName: 'Khách kiểm thử Nguyễn', email: `smoke-${token}@example.com`, phone: phone(), password, confirmPassword: password}});
    await customer.login(account.email, password);
    assert.equal((await customer.request('/api/profile')).fullName, 'Khách kiểm thử Nguyễn');
    await customer.request('/api/admin/orders', {status: 403});
    for (const page of ['/customer/profile', '/customer/addresses', '/cart', '/checkout', '/orders']) await customer.request(page);
    const address = await customer.request('/api/addresses', {method: 'POST', status: 201,
        body: {addressLine: '123 Đường Nguyễn Văn Cừ, TP. Hồ Chí Minh', note: 'Gọi trước khi giao', isDefault: true}});
    assert.equal(address.isDefault, true);
    const updated = await customer.request('/api/addresses/' + address.id, {method: 'PUT',
        body: {addressLine: '456 Đường Nguyễn Văn Cừ, TP. Hồ Chí Minh', note: 'Địa chỉ đã cập nhật', isDefault: true}});
    assert(updated.addressLine.startsWith('456'));

    const manager = await admin.login(process.env.CRAVE_SMOKE_ADMIN_EMAIL, process.env.CRAVE_SMOKE_ADMIN_PASSWORD);
    assert.equal(manager.role, 'ADMIN');
    await admin.request('/api/cart', {status: 403});
    const category = await admin.request('/api/admin/categories', {method: 'POST', status: 201,
        body: {name: `Kiểm thử ${token}`, description: 'Dữ liệu riêng của kiểm thử HTTP'}});
    const food = await admin.request('/api/admin/foods', {method: 'POST', status: 201,
        body: {categoryId: category.id, name: `Món thử ${token}`, price: 25000,
            imageUrl: '/assets/images/food-placeholder.svg', description: 'Món kiểm thử', status: 'AVAILABLE'}});
    const option = await admin.request(`/api/admin/foods/${food.id}/options`, {method: 'POST', status: 201,
        body: {optionType: 'SIZE', name: 'Phần lớn', extraPrice: 1000, status: 'ACTIVE'}});
    await customer.request('/api/cart/items', {method: 'POST',
        body: {foodId: food.id, quantity: 2, optionIds: [option.id], note: 'Ít cay'}});
    const cart = await customer.request('/api/cart');
    assert.equal(Number(cart.subtotal), 52000);
    const today = new Intl.DateTimeFormat('en-CA', {timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit'}).format(new Date());
    const promotion = await admin.request('/api/admin/promotions', {method: 'POST', status: 201,
        body: {code: `SMOKE${token}`.toUpperCase(), name: 'Ưu đãi kiểm thử', discountType: 'PERCENT', discountValue: 10,
            minimumOrderValue: 0, maximumDiscount: null, startAt: today + 'T00:00:00', endAt: today + 'T23:59:59', status: 'ACTIVE'}});
    const voucher = await customer.request('/api/promotions/validate', {method: 'POST',
        body: {code: promotion.code, subtotal: 52000}});
    assert.equal(Number(voucher.discountAmount), 5200);
    const order = await customer.request('/api/orders', {method: 'POST', status: 201,
        body: {fulfillmentType: 'PICKUP', receiverName: account.fullName, receiverPhone: account.phone,
            deliveryAddress: 'Địa chỉ cũ phải được bỏ', paymentMethod: 'CASH', promotionId: promotion.promotionId, customerNote: 'Kiểm thử nhận tại quán'}});
    assert.equal(order.deliveryAddress, null);
    assert.equal(Number(order.deliveryFee), 0);
    assert.equal(Number(order.totalAmount), 46800);
    assert.equal(Number(order.payment.amount), 46800);
    assert.equal((await customer.request('/api/cart')).items.length, 0);
    await customer.request('/orders/' + order.orderId);
    await customer.request('/api/orders/' + order.orderId);
    await customer.request('/api/orders', {method: 'POST', status: 400,
        body: {fulfillmentType: 'PICKUP', receiverName: account.fullName, receiverPhone: account.phone, paymentMethod: 'CASH'}});
    for (const status of ['PREPARING', 'COMPLETED']) await admin.request(`/api/admin/orders/${order.orderId}/status`,
        {method: 'PATCH', body: {status, note: 'Kiểm thử HTTP'}});
    await admin.request(`/api/admin/payments/${order.payment.paymentId}/status`,
        {method: 'PATCH', body: {status: 'SUCCESS', transactionRef: 'SMOKE-' + token}});
    const completed = await customer.request('/api/orders/' + order.orderId);
    assert.equal(completed.status, 'COMPLETED');
    assert.equal(completed.payment.status, 'SUCCESS');
    await admin.request(`/api/admin/payments/${order.payment.paymentId}/status`, {method: 'PATCH', status: 400, body: {status: 'PENDING'}});
    await customer.request('/api/cart/items', {method: 'POST', body: {foodId: food.id, quantity: 1, optionIds: [], note: ''}});
    const delivery = await customer.request('/api/orders', {method: 'POST', status: 201,
        body: {fulfillmentType: 'DELIVERY', receiverName: account.fullName, receiverPhone: account.phone,
            deliveryAddress: updated.addressLine, paymentMethod: 'BANK_TRANSFER'}});
    assert.equal(Number(delivery.deliveryFee), 15000);
    assert.equal(Number(delivery.totalAmount), 40000);
    for (const status of ['PREPARING', 'DELIVERING', 'COMPLETED']) await admin.request(`/api/admin/orders/${delivery.orderId}/status`,
        {method: 'PATCH', body: {status, note: 'Kiểm thử giao tận nơi'}});
    await admin.request(`/api/admin/payments/${delivery.payment.paymentId}/status`,
        {method: 'PATCH', body: {status: 'SUCCESS', transactionRef: 'DELIVERY-' + token}});
    await admin.request(`/api/admin/payments/${delivery.payment.paymentId}/status`,
        {method: 'PATCH', body: {status: 'REFUNDED', transactionRef: 'REFUND-' + token}});
    assert.equal((await customer.request('/api/orders/' + delivery.orderId)).payment.status, 'REFUNDED');
    if (!apiOnly) {
        const overview = await admin.request('/api/admin/overview?from=' + today + '&to=' + today);
        assert(Number(overview.summary.revenue) >= 46800);
        assert(overview.topItems.some(item => item.foodId === food.id));
        for (const page of ['/admin', '/admin/orders', '/admin/menu', '/admin/categories', '/admin/customers', '/admin/employees', '/admin/promotions', '/admin/reports']) await admin.request(page);
    }
    assert((await admin.request('/api/admin/customers')).some(item => item.id === account.id));
    await admin.request(`/api/admin/customers/${account.id}/status`, {method: 'PATCH', body: {status: 'LOCKED'}});
    await customer.request('/api/profile', {status: 401});
    await admin.request(`/api/admin/customers/${account.id}/status`, {method: 'PATCH', body: {status: 'ACTIVE'}});
    await customer.login(account.email, password);
    for (const [role, allowed, forbidden] of [
        ['MENU_MANAGER', '/api/admin/foods', '/api/admin/orders'],
        ['ORDER_STAFF', '/api/admin/orders', '/api/admin/employees'],
        ['PROMOTION_MANAGER', '/api/admin/promotions', '/api/admin/foods'],
        ['HR_MANAGER', '/api/admin/employees', '/api/admin/promotions']
    ]) {
        const employee = await admin.request('/api/admin/employees', {method: 'POST', status: 201,
            body: {fullName: 'Nhân viên kiểm thử', email: `${role.toLowerCase()}-${token}@example.com`, phone: phone(),
                password, role, hireDate: today, address: 'TP. Hồ Chí Minh'}});
        const staff = new Client();
        await staff.login(employee.email, password);
        await staff.request(allowed);
        await staff.request(forbidden, {status: 403});
        await admin.request(`/api/admin/employees/${employee.id}/status`, {method: 'PATCH', body: {status: 'ON_LEAVE'}});
        await staff.request(allowed, {status: 401});
    }
    await admin.request(`/api/admin/employees/${manager.id}/status`, {method: 'PATCH', status: 409, body: {status: 'ON_LEAVE'}});
    await admin.request(`/api/admin/promotions/${promotion.promotionId}/status`, {method: 'PATCH', body: {status: 'INACTIVE'}});
    await customer.request('/api/addresses/' + address.id, {method: 'DELETE', status: 204});
    await customer.request('/api/auth/logout', {method: 'POST'});
    await customer.request('/api/profile', {status: 401});
    console.log(`Live HTTP smoke passed: ${checks} requests, real JSP/MySQL/account/cart/order/payment/admin flows.`);
    console.log(`Created isolated smoke fixtures: ${token}`);
})().catch(error => {console.error(error.message); process.exitCode = 1;});
