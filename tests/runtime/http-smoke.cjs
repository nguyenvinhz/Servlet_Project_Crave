// Run against a disposable seeded database. This creates accounts, menu items, promotions and orders.
// CRAVE_SMOKE_ADMIN_EMAIL / CRAVE_SMOKE_ADMIN_PASSWORD identify an existing admin in that database.
const assert = require('node:assert/strict');
const {randomBytes} = require('node:crypto');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8081/crave').replace(/\/$/, '');
const origin = new URL(base).origin;
const apiOnly = process.argv.includes('--api-only');
const token = Date.now().toString(36) + randomBytes(2).toString('hex');
const password = randomBytes(14).toString('base64url');
let customerPassword = password;
const inboxBase = (process.env.CRAVE_SMOKE_INBOX_URL || 'http://127.0.0.1:2526').replace(/\/$/, '');
assert(['127.0.0.1', 'localhost', '[::1]'].includes(new URL(inboxBase).hostname), 'SMTP test inbox must use loopback.');
const phone = () => '09' + String(Math.floor(Math.random() * 100000000)).padStart(8, '0');
let checks = 0;

async function verificationCode(email, purpose) {
    for (let attempt = 0; attempt < 30; attempt++) {
        const response = await fetch(inboxBase + '/messages?email=' + encodeURIComponent(email) + '&purpose=' + purpose);
        if (response.ok) {
            const message = await response.json();
            assert.equal(message.email.toLowerCase(), email.toLowerCase());
            assert.equal(message.purpose, purpose);
            assert.match(message.code, /^[0-9]{6}$/);
            return message.code;
        }
        assert.equal(response.status, 404, 'Unexpected test inbox response.');
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw new Error('The local SMTP fixture did not receive a verification message.');
}

async function requestCode(client, path, body) {
    const result = await client.request(path, {method: 'POST', body});
    assert(!/verificationCode|otpCode|codeHash|passwordHash/.test(JSON.stringify(result)), 'Code or hash leaked through API response.');
    return result;
}

async function resetPassword(client, email, nextPassword, code) {
    return client.request('/api/auth/password/reset', {method: 'POST',
        body: {email, verificationCode: code, password: nextPassword, confirmPassword: nextPassword}});
}

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
    const registration = {fullName: 'Khách kiểm thử Nguyễn', email: `smoke-${token}@example.com`, phone: phone(), password, confirmPassword: password};
    await guest.request('/api/auth/register', {method: 'POST', status: 400, body: registration});
    await requestCode(guest, '/api/auth/register/code', {email: registration.email});
    const registerCode = await verificationCode(registration.email, 'REGISTER');
    const wrongRegisterCode = registerCode === '000000' ? '111111' : '000000';
    await guest.request('/api/auth/register', {method: 'POST', status: 400,
        body: {...registration, verificationCode: wrongRegisterCode}});
    const account = await guest.request('/api/auth/register', {method: 'POST', status: 201,
        body: {...registration, verificationCode: registerCode}});
    await customer.login(account.email, customerPassword);
    assert.equal((await customer.request('/api/profile')).fullName, 'Khách kiểm thử Nguyễn');
    // Codes bind to the account/purpose, and successful reset revokes existing sessions.
    await guest.request('/api/auth/password/reset', {method: 'POST', status: 400,
        body: {email: account.email, verificationCode: registerCode, password, confirmPassword: password}});
    await requestCode(guest, '/api/auth/password/reset/code', {email: account.email});
    const resetCode = await verificationCode(account.email, 'PASSWORD_RESET');
    await guest.request('/api/auth/password/reset', {method: 'POST', status: 400,
        body: {email: `other-${token}@example.com`, verificationCode: resetCode, password, confirmPassword: password}});
    const resetPasswordValue = randomBytes(14).toString('base64url');
    await resetPassword(guest, account.email, resetPasswordValue, resetCode);
    await customer.request('/api/profile', {status: 401});
    await guest.request('/api/auth/password/reset', {method: 'POST', status: 400,
        body: {email: account.email, verificationCode: resetCode, password, confirmPassword: password}});
    await guest.request('/api/auth/login', {method: 'POST', status: 401, body: {email: account.email, password}});
    customerPassword = resetPasswordValue;
    await customer.login(account.email, customerPassword);
    await requestCode(customer, '/api/auth/password/change/code', {currentPassword: customerPassword});
    const changeCode = await verificationCode(account.email, 'PASSWORD_CHANGE');
    const changedPassword = randomBytes(14).toString('base64url');
    await customer.request('/api/auth/password/change', {method: 'POST',
        body: {verificationCode: changeCode, password: changedPassword, confirmPassword: changedPassword}});
    await customer.request('/api/profile', {status: 401});
    customerPassword = changedPassword;
    await customer.login(account.email, customerPassword);
    await customer.request('/api/auth/password/change', {method: 'POST', status: 400,
        body: {verificationCode: changeCode, password, confirmPassword: password}});
    const unknownEmail = `unknown-${token}@example.com`;
    await requestCode(guest, '/api/auth/password/reset/code', {email: unknownEmail});
    assert.equal((await fetch(inboxBase + '/messages?email=' + encodeURIComponent(unknownEmail) + '&purpose=PASSWORD_RESET')).status, 404);
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
    await requestCode(guest, '/api/auth/password/reset/code', {email: manager.email});
    const adminCode = await verificationCode(manager.email, 'PASSWORD_RESET');
    const nextAdminPassword = randomBytes(14).toString('base64url');
    await resetPassword(guest, manager.email, nextAdminPassword, adminCode);
    await admin.request('/api/admin/customers', {status: 401});
    assert.equal((await admin.login(manager.email, nextAdminPassword)).role, 'ADMIN');
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
    const deliveryPolicy = await customer.request('/api/delivery/policy');
    assert.equal(deliveryPolicy.configured, true);
    assert.equal('bands' in deliveryPolicy, false);
    const destination = {latitude: deliveryPolicy.store.latitude + 0.02, longitude: deliveryPolicy.store.longitude};
    const deliveryQuote = await customer.request('/api/delivery/quote', {method: 'POST', body: destination});
    assert.equal(deliveryQuote.deliverable, true);
    assert.equal(Number(deliveryQuote.deliveryFee), 4000);
    assert.equal('distanceKm' in deliveryQuote, false);
    for (const [km, fee] of [[6, 9500], [8, 15000], [9, 18000]]) {
        const perKmQuote = await customer.request('/api/delivery/quote', {method: 'POST',
            body: {latitude: deliveryPolicy.store.latitude + km / 6371.0088 * 180 / Math.PI,
                longitude: destination.longitude}});
        assert.equal(perKmQuote.deliverable, true);
        assert.equal(Number(perKmQuote.deliveryFee), fee);
        assert.equal('bands' in perKmQuote, false);
    }
    const coordinates = await customer.request('/api/delivery/search', {method: 'POST',
        body: {address: `${destination.latitude}, ${destination.longitude}`}});
    assert.equal(coordinates[0].latitude, destination.latitude);
    assert.equal(coordinates[0].longitude, destination.longitude);
    await customer.request('/api/orders', {method: 'POST', status: 400,
        body: {fulfillmentType: 'DELIVERY', receiverName: account.fullName, receiverPhone: account.phone,
            deliveryAddress: updated.addressLine, paymentMethod: 'BANK_TRANSFER'}});
    const outsideQuote = await customer.request('/api/delivery/quote', {method: 'POST',
        body: {latitude: deliveryPolicy.store.latitude + 0.2, longitude: destination.longitude}});
    assert.equal(outsideQuote.deliverable, false);
    await customer.request('/api/orders', {method: 'POST', status: 400,
        body: {fulfillmentType: 'DELIVERY', receiverName: account.fullName, receiverPhone: account.phone,
            deliveryLatitude: deliveryPolicy.store.latitude + 0.2, deliveryLongitude: destination.longitude,
            deliveryAddress: updated.addressLine, paymentMethod: 'BANK_TRANSFER'}});
    const delivery = await customer.request('/api/orders', {method: 'POST', status: 201,
        body: {fulfillmentType: 'DELIVERY', receiverName: account.fullName, receiverPhone: account.phone,
            deliveryAddress: updated.addressLine, deliveryLatitude: destination.latitude,
            deliveryLongitude: destination.longitude, paymentMethod: 'BANK_TRANSFER'}});
    assert.equal(Number(delivery.deliveryFee), 4000);
    assert.equal(Number(delivery.totalAmount), 29000);
    assert.equal(delivery.deliveryLatitude, destination.latitude);
    assert.equal(delivery.deliveryLongitude, destination.longitude);
    assert.equal(Number(delivery.payment.amount), 29000);
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
    await customer.login(account.email, customerPassword);
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
        await requestCode(guest, '/api/auth/password/reset/code', {email: employee.email});
        const staffCode = await verificationCode(employee.email, 'PASSWORD_RESET');
        const nextStaffPassword = randomBytes(14).toString('base64url');
        await resetPassword(guest, employee.email, nextStaffPassword, staffCode);
        await staff.request(allowed, {status: 401});
        assert.equal((await staff.login(employee.email, nextStaffPassword)).role, role);
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
