// Run only against a disposable, locally hosted, seeded test database.
// Creates uniquely named customers, employees, menu fixtures and orders; leaves the seeded admin password unchanged.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {randomBytes} = require('node:crypto');
const base = (process.env.CRAVE_SMOKE_BASE_URL || 'http://127.0.0.1:8081/crave').replace(/\/$/, '');
const origin = new URL(base).origin;
const context = new URL(base).pathname.replace(/\/$/, '');
const inboxBase = (process.env.CRAVE_SMOKE_INBOX_URL || 'http://127.0.0.1:2526').replace(/\/$/, '');
const loopback = value => ['127.0.0.1', 'localhost', '[::1]'].includes(new URL(value).hostname);
assert(loopback(base), 'HTTP smoke writes require a loopback test application.');
assert(loopback(inboxBase), 'SMTP test inbox must use loopback.');
assert(process.env.CRAVE_SMOKE_ADMIN_EMAIL && process.env.CRAVE_SMOKE_ADMIN_PASSWORD,
    'Set CRAVE_SMOKE_ADMIN_EMAIL and CRAVE_SMOKE_ADMIN_PASSWORD for the disposable test database.');
const token = Date.now().toString(36) + randomBytes(2).toString('hex');
const password = process.env.CRAVE_SMOKE_CUSTOMER_PASSWORD || randomBytes(14).toString('base64url');
const email = process.env.CRAVE_SMOKE_CUSTOMER_EMAIL || 'smoke-' + token + '@example.com';
const account = {fullName:'Khách kiểm thử Nguyễn', email, phone:'09' + String(Math.floor(Math.random() * 100000000)).padStart(8, '0')};
const phone = () => '09' + String(Math.floor(Math.random() * 100000000)).padStart(8, '0');
const today = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit', day:'2-digit'}).format(new Date());
let checks = 0;

function decode(value) {
    return value.replace(/&(?:#(\d+)|#x([\da-f]+)|amp|lt|gt|quot|apos|nbsp);/gi, (entity, decimal, hexadecimal) => {
        if (decimal) return String.fromCodePoint(Number(decimal));
        if (hexadecimal) return String.fromCodePoint(parseInt(hexadecimal, 16));
        return {'&amp;':'&', '&lt;':'<', '&gt;':'>', '&quot;':'"', '&apos;':"'", '&nbsp;':' '}[entity.toLowerCase()] || entity;
    });
}
const text = html => decode(html.replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
function attributes(source) {
    const values = {};
    for (const match of source.matchAll(/([\w:-]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>\x60]+)))?/g)) {
        values[match[1].toLowerCase()] = decode(match[2] ?? match[3] ?? match[4] ?? '');
    }
    return values;
}
function forms(html) {
    return [...html.matchAll(/<form\b([^>]*)>([\s\S]*?)<\/form>/gi)].map(match => ({
        ...attributes(match[1]), fields:Object.fromEntries([...match[2].matchAll(/<input\b([^>]*)>/gi)]
            .map(input => attributes(input[1])).filter(input => input.name).map(input => [input.name, input.value || '']))
    }));
}
function row(html, marker, tag = 'tr') {
    const rows = [...html.matchAll(new RegExp('<' + tag + '\\b[^>]*>[\\s\\S]*?<\\/' + tag + '>', 'gi'))];
    const found = rows.find(match => text(match[0]).includes(marker));
    assert(found, 'Server page omitted fixture: ' + marker);
    return found[0];
}
function inputValue(html, name) {
    return [...html.matchAll(/<input\b([^>]*)>/gi)].map(match => attributes(match[1])).find(input => input.name === name)?.value;
}
function recordId(html, marker, tag = 'tr', name = 'id') {
    const found = forms(row(html, marker, tag)).map(form => form.fields[name]).find(Boolean);
    assert(found, 'Server form omitted fixture ID for ' + marker);
    return found;
}
function alertText(html) {
    const alerts = [...html.matchAll(/<(div|p)\b([^>]*\brole=["']alert["'][^>]*)>([\s\S]*?)<\/\1>/gi)];
    return alerts.filter(match => !Object.hasOwn(attributes(match[2]), 'hidden')).map(match => text(match[3])).filter(Boolean).join(' ');
}
function formError(result) {
    assert(alertText(result.html), 'Expected server-rendered form validation error.');
    return result.html;
}
function money(html, label) {
    const rows = [...html.matchAll(/<div\b[^>]*class=["'][^"']*summary-(?:total-)?row[^"']*["'][^>]*>([\s\S]*?)<\/div>/gi)];
    const found = rows.map(match => text(match[1])).find(value => value.startsWith(label));
    assert(found, 'Summary omitted ' + label);
    return Number(found.substring(label.length).replace(/[^\d]/g, ''));
}
async function verificationCode(address, purpose) {
    for (let attempt = 0; attempt < 40; attempt++) {
        const response = await fetch(inboxBase + '/messages?email=' + encodeURIComponent(address) + '&purpose=' + purpose);
        if (response.ok) {
            const message = await response.json();
            assert.equal(message.email.toLowerCase(), address.toLowerCase());
            assert.equal(message.purpose, purpose);
            assert.match(message.code, /^[0-9]{6}$/);
            return message.code;
        }
        assert.equal(response.status, 404, 'Unexpected test inbox response.');
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw new Error('The local SMTP fixture did not receive a verification message.');
}
class Client {
    cookies = new Map();
    async request(route, {method = 'GET', body, status = 200, originHeader = origin, fetchSite, kind = 'html'} = {}) {
        const headers = {Accept:kind === 'html' ? 'text/html' : 'text/plain'};
        if (originHeader !== null) headers.Origin = originHeader;
        if (fetchSite) headers['Sec-Fetch-Site'] = fetchSite;
        if (this.cookies.size) headers.Cookie = [...this.cookies].map(([key, value]) => key + '=' + value).join('; ');
        let encoded;
        if (body !== undefined) {
            headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
            encoded = new URLSearchParams();
            for (const [key, value] of Object.entries(body)) {
                for (const item of Array.isArray(value) ? value : [value]) {
                    if (item !== undefined && item !== null) encoded.append(key, String(item));
                }
            }
        }
        const response = await fetch(base + route, {method, headers, redirect:'manual', body:encoded});
        for (const cookie of response.headers.getSetCookie()) {
            const pair = cookie.split(';')[0], separator = pair.indexOf('=');
            if (separator > 0) this.cookies.set(pair.substring(0, separator), pair.substring(separator + 1));
        }
        const html = await response.text();
        assert.equal(response.status, status, method + ' ' + route + ': ' + text(html).slice(0, 400));
        checks++;
        if (status === 200 && kind === 'html') {
            assert.match(response.headers.get('content-type') || '', /^text\/html\b/i, route + ': expected HTML response');
            assert.equal((html.match(/<html\b/gi) || []).length, 1, route + ': duplicate HTML document');
            assert.equal((html.match(/<body\b/gi) || []).length, 1, route + ': duplicate body');
            assert(!html.includes('HTTP Status 500'), route + ': JSP exception');
            assert(/charset\s*=\s*["']?UTF-8/i.test(html), route + ': missing UTF-8 metadata');
            assert(!/passwordHash|password_hash|codeHash/.test(html), route + ': credential hash leaked');
            for (const form of forms(html)) {
                assert(!form.action || !form.action.includes('/api/'), route + ': form still targets an API');
            }
        }
        return {html, status:response.status, location:response.headers.get('location'), headers:response.headers};
    }
    async page(route) { return (await this.request(route)).html; }
    async follow(result) {
        assert(result.location, 'Missing redirect target.');
        const destination = new URL(result.location, base + '/');
        assert.equal(destination.origin, origin, 'Redirect escaped the test application.');
        assert(destination.pathname.startsWith(context + '/'), 'Redirect escaped the application context.');
        return this.page(destination.pathname.substring(context.length) + destination.search);
    }
    async save(route, body) {
        const result = await this.request(route, {method:'POST', body, status:302});
        const html = await this.follow(result);
        assert(!alertText(html), route + ': ' + alertText(html));
        return html;
    }
    async login(address, secret) {
        return this.save('/auth/login', {email:address, password:secret});
    }
}
async function requestCode(client, route, body) {
    const result = await client.request(route, {method:'POST', body:{...body, action:'sendCode'}});
    assert(!alertText(result.html), 'Code request failed: ' + alertText(result.html));
    return result;
}
async function redirectedToLogin(client, route) {
    const result = await client.request(route, {status:302});
    assert(new URL(result.location, base).pathname.endsWith('/auth/login'), 'Unauthorized request did not redirect to login.');
}
async function rejectedForm(client, route, body) {
    return formError(await client.request(route, {method:'POST', body}));
}
function orderId(result) {
    assert(result.location, 'Order creation did not redirect.');
    const found = /\/orders\/([A-Za-z0-9_-]+)$/.exec(new URL(result.location, base).pathname);
    assert(found, 'Order creation did not redirect to an order page.');
    return found[1];
}

(async () => {
    const guest = new Client(), customer = new Client(), admin = new Client();
    const health = await guest.request('/health', {kind:'text'});
    assert.match(health.html, /database=UP/);
    for (const page of ['/menu', '/menu/detail?id=MA02', '/promotions', '/auth/login', '/auth/register', '/auth/forgot-password']) await guest.page(page);
    for (const route of ['/admin', '/admin/customers', '/cart', '/customer/profile', '/auth/change-password']) await redirectedToLogin(guest, route);
    // Same-origin POSTs work; cross-origin and cross-site POSTs are rejected before any action runs.
    await guest.request('/auth/login', {method:'POST', body:{}, status:403, originHeader:'https://untrusted.example'});
    await guest.request('/auth/register', {method:'POST', body:{action:'sendCode', email}, status:403, fetchSite:'cross-site'});
    await guest.request('/api/profile', {status:404});

    const registration = {...account, password, confirmPassword:password, action:'register'};
    await rejectedForm(guest, '/auth/register', registration);
    await requestCode(guest, '/auth/register', {email});
    const registerCode = await verificationCode(email, 'REGISTER');
    await rejectedForm(guest, '/auth/register', {...registration, verificationCode:registerCode === '000000' ? '111111' : '000000'});
    await guest.save('/auth/register', {...registration, verificationCode:registerCode});
    await customer.login(email, password);
    assert.equal(inputValue(await customer.page('/customer/profile'), 'fullName'), account.fullName);

    // Codes are bound to purpose/account; successful password operations revoke existing sessions.
    await rejectedForm(guest, '/auth/forgot-password', {action:'reset', email, verificationCode:registerCode, password, confirmPassword:password});
    await requestCode(guest, '/auth/forgot-password', {email});
    const resetCode = await verificationCode(email, 'PASSWORD_RESET');
    await rejectedForm(guest, '/auth/forgot-password', {action:'reset', email:'other-' + token + '@example.com', verificationCode:resetCode, password, confirmPassword:password});
    const temporaryPassword = randomBytes(14).toString('base64url');
    await guest.save('/auth/forgot-password', {action:'reset', email, verificationCode:resetCode, password:temporaryPassword, confirmPassword:temporaryPassword});
    await redirectedToLogin(customer, '/customer/profile');
    await rejectedForm(guest, '/auth/forgot-password', {action:'reset', email, verificationCode:resetCode, password, confirmPassword:password});
    await rejectedForm(guest, '/auth/login', {email, password});
    await customer.login(email, temporaryPassword);
    await requestCode(customer, '/auth/change-password', {currentPassword:temporaryPassword});
    const changeCode = await verificationCode(email, 'PASSWORD_CHANGE');
    await customer.save('/auth/change-password', {action:'change', verificationCode:changeCode, password, confirmPassword:password});
    await redirectedToLogin(customer, '/customer/profile');
    await customer.login(email, password);
    await rejectedForm(customer, '/auth/change-password', {action:'change', verificationCode:changeCode, password:temporaryPassword, confirmPassword:temporaryPassword});
    const unknownEmail = 'unknown-' + token + '@example.com';
    await requestCode(guest, '/auth/forgot-password', {email:unknownEmail});
    assert.equal((await fetch(inboxBase + '/messages?email=' + encodeURIComponent(unknownEmail) + '&purpose=PASSWORD_RESET')).status, 404);
    await customer.request('/admin/orders', {status:403});
    for (const route of ['/customer/profile', '/customer/addresses', '/cart', '/checkout', '/orders']) await customer.page(route);
    await customer.save('/customer/profile', {fullName:account.fullName, email, phone:account.phone});
    const addressLine = '123 Đường Nguyễn Văn Cừ, TP. Hồ Chí Minh';
    let addressPage = await customer.save('/customer/addresses', {action:'save', addressLine, note:'Gọi trước khi giao', isDefault:'true'});
    const addressId = recordId(addressPage, addressLine, 'article');
    addressPage = await customer.save('/customer/addresses', {action:'save', id:addressId,
        addressLine:'456 Đường Nguyễn Văn Cừ, TP. Hồ Chí Minh', note:'Địa chỉ đã cập nhật', isDefault:'true'});
    assert(text(addressPage).includes('456 Đường Nguyễn Văn Cừ'));

    await admin.login(process.env.CRAVE_SMOKE_ADMIN_EMAIL, process.env.CRAVE_SMOKE_ADMIN_PASSWORD);
    await admin.request('/cart', {status:403});
    await admin.request('/customer/addresses', {status:403});
    const categoryName = 'Kiểm thử ' + token;
    const categoryPage = await admin.save('/admin/categories', {action:'create', name:categoryName, description:'Dữ liệu riêng của kiểm thử HTTP'});
    const categoryId = recordId(categoryPage, categoryName);
    const foodName = 'Món thử ' + token;
    const foodPage = await admin.save('/admin/menu', {action:'create', categoryId, name:foodName, price:25000,
        imageUrl:'/assets/images/food-placeholder.svg', description:'Món kiểm thử', status:'AVAILABLE'});
    const foodId = recordId(foodPage, foodName);
    const optionPage = await admin.save('/admin/menu', {action:'optionCreate', foodId, optionType:'SIZE', name:'Phần lớn ' + token, extraPrice:1000, status:'ACTIVE'});
    const optionId = recordId(optionPage, 'Phần lớn ' + token);
    await customer.save('/cart/items', {action:'add', foodId, quantity:2, optionIds:[optionId], note:'Ít cay'});
    let cartPage = await customer.page('/cart');
    assert.equal(money(cartPage, 'Tạm tính'), 52000);
    const cartItemId = forms(cartPage).map(form => form.fields.cartItemId).find(Boolean);
    assert(cartItemId, 'Cart JSP omitted item forms.');
    const editor = await customer.page('/cart?edit=' + encodeURIComponent(cartItemId));
    assert(editor.includes('cartItemEditorForm') && editor.includes(optionId), 'Cart customization did not load.');
    const code = ('SMOKE' + token).toUpperCase();
    const promotionPage = await admin.save('/admin/promotions', {action:'create', code, name:'Ưu đãi kiểm thử ' + token,
        discountType:'PERCENT', discountValue:10, minimumOrderValue:0, maximumDiscount:'',
        startAt:today + 'T00:00:00', endAt:today + 'T23:59:59', status:'ACTIVE'});
    const promotionId = recordId(promotionPage, code, 'article');
    cartPage = await customer.save('/cart/promotion', {code});
    assert.equal(money(cartPage, 'Tiền món sau giảm'), 46800);
    const pickupResult = await customer.request('/checkout', {method:'POST', status:302, body:{action:'placeOrder',
        fulfillmentType:'PICKUP', receiverName:account.fullName, receiverPhone:account.phone, deliveryAddress:'Địa chỉ cũ phải được bỏ',
        paymentMethod:'CASH', customerNote:'Kiểm thử nhận tại quán'}});
    const pickupId = orderId(pickupResult);
    let pickup = await customer.follow(pickupResult);
    assert.equal(money(pickup, 'Phí giao hàng'), 0);
    assert.equal(money(pickup, 'Tổng cộng'), 46800);
    assert(!text(pickup).includes('Địa chỉ cũ phải được bỏ'), 'Pickup retained delivery address.');
    assert(text(await customer.page('/cart')).includes('Giỏ hàng của bạn đang trống'));
    await rejectedForm(customer, '/checkout', {action:'placeOrder', fulfillmentType:'PICKUP', receiverName:account.fullName,
        receiverPhone:account.phone, paymentMethod:'CASH'});
    const adminOrders = await admin.page('/admin/orders?search=' + pickupId);
    const paymentId = recordId(adminOrders, pickupId, 'tr', 'paymentId');
    for (const status of ['PREPARING', 'COMPLETED']) await admin.save('/admin/orders', {orderId:pickupId, status, note:'Kiểm thử HTTP'});
    await admin.save('/admin/payments', {paymentId, status:'SUCCESS', transactionRef:'SMOKE-' + token});
    pickup = await customer.page('/orders/' + pickupId);
    assert(text(pickup).includes('COMPLETED') && /CASH\s*·\s*SUCCESS/.test(text(pickup)), 'Order/payment updates were not rendered.');
    const invalidPayment = await admin.request('/admin/payments', {method:'POST', status:302, body:{paymentId, status:'PENDING'}});
    assert(alertText(await admin.follow(invalidPayment)), 'Invalid payment transition was accepted.');

    await customer.save('/cart/items', {action:'add', foodId, quantity:1, note:''});
    const checkout = await customer.page('/checkout');
    const map = /<div\b[^>]*id="deliveryMap"[^>]*>/i.exec(checkout);
    assert(map, 'Delivery map/store data missing.');
    const store = attributes(map[0]);
    const latitude = Number(store['data-store-latitude']), longitude = Number(store['data-store-longitude']);
    assert(Number.isFinite(latitude) && Number.isFinite(longitude), 'Store coordinates missing.');
    const deliveryForm = {fulfillmentType:'DELIVERY', receiverName:account.fullName, receiverPhone:account.phone,
        deliveryAddress:'456 Đường Nguyễn Văn Cừ, TP. Hồ Chí Minh', paymentMethod:'CASH'};
    for (const [km, fee] of [[2, 4000], [6, 9500], [8, 15000], [9, 18000]]) {
        const quoted = await customer.request('/checkout', {method:'POST', body:{...deliveryForm, action:'quote',
            deliveryLatitude:latitude + km / 6371.0088 * 180 / Math.PI, deliveryLongitude:longitude}});
        assert.equal(money(quoted.html, 'Phí giao hàng'), fee);
    }
    const destination = latitude + 0.02;
    const searched = await customer.request('/checkout', {method:'POST', body:{...deliveryForm, action:'search',
        deliveryAddress:destination + ', ' + longitude}});
    assert(searched.html.includes('location-result'), 'Coordinate search did not render locations.');
    await rejectedForm(customer, '/checkout', {...deliveryForm, action:'placeOrder'});
    const outside = await customer.request('/checkout', {method:'POST', body:{...deliveryForm, action:'quote',
        deliveryLatitude:latitude + 0.2, deliveryLongitude:longitude}});
    assert(alertText(outside.html) || /không|ngoài|phạm vi/i.test(text(outside.html)), 'Outside delivery region was accepted.');
    await rejectedForm(customer, '/checkout', {...deliveryForm, action:'placeOrder',
        deliveryLatitude:latitude + 0.2, deliveryLongitude:longitude});
    const deliveryResult = await customer.request('/checkout', {method:'POST', status:302, body:{...deliveryForm, action:'placeOrder',
        deliveryLatitude:destination, deliveryLongitude:longitude, customerNote:'Kiểm thử giao tận nơi'}});
    const deliveryId = orderId(deliveryResult);
    const delivery = await customer.follow(deliveryResult);
    assert.equal(money(delivery, 'Phí giao hàng'), 4000);
    assert.equal(money(delivery, 'Tổng cộng'), 29000);
    const deliveryPaymentId = recordId(await admin.page('/admin/orders?search=' + deliveryId), deliveryId, 'tr', 'paymentId');
    for (const status of ['PREPARING', 'DELIVERING', 'COMPLETED']) await admin.save('/admin/orders', {orderId:deliveryId, status, note:'Kiểm thử giao tận nơi'});
    await admin.save('/admin/payments', {paymentId:deliveryPaymentId, status:'SUCCESS', transactionRef:'DELIVERY-' + token});
    await admin.save('/admin/payments', {paymentId:deliveryPaymentId, status:'REFUNDED', transactionRef:'REFUND-' + token});
    assert(/CASH\s*·\s*REFUNDED/.test(text(await customer.page('/orders/' + deliveryId))), 'Refund status not rendered.');
    for (const route of ['/admin', '/admin/orders', '/admin/menu', '/admin/categories', '/admin/customers', '/admin/employees', '/admin/promotions', '/admin/reports']) await admin.page(route);
    const reports = await admin.page('/admin/reports?from=' + today + '&to=' + today);
    assert(text(reports).includes(foodName), 'Report omitted the completed smoke order.');
    const csv = await admin.request('/admin/reports?from=' + today + '&to=' + today + '&download=csv', {kind:'text'});
    assert.match(csv.headers.get('content-type') || '', /text\/csv/);
    assert(csv.html.includes(today), 'CSV report omitted the selected day.');

    const customerId = recordId(await admin.page('/admin/customers?keyword=' + encodeURIComponent(email)), email);
    await admin.save('/admin/customers', {action:'status', id:customerId, status:'LOCKED'});
    await redirectedToLogin(customer, '/customer/profile');
    await admin.save('/admin/customers', {action:'status', id:customerId, status:'ACTIVE'});
    await customer.login(email, password);
    for (const [role, allowed, forbidden] of [
        ['MENU_MANAGER', '/admin/menu', '/admin/orders'], ['ORDER_STAFF', '/admin/orders', '/admin/employees'],
        ['PROMOTION_MANAGER', '/admin/promotions', '/admin/menu'], ['HR_MANAGER', '/admin/employees', '/admin/promotions']
    ]) {
        const staffEmail = role.toLowerCase() + '-' + token + '@example.com';
        const staffPassword = randomBytes(14).toString('base64url');
        const employees = await admin.save('/admin/employees', {action:'create', fullName:'Nhân viên kiểm thử', email:staffEmail,
            phone:phone(), password:staffPassword, role, hireDate:today, address:'TP. Hồ Chí Minh'});
        const staffId = recordId(employees, staffEmail);
        const staff = new Client();
        await staff.login(staffEmail, staffPassword);
        await staff.page(allowed);
        await staff.request(forbidden, {status:403});
        await requestCode(guest, '/auth/forgot-password', {email:staffEmail});
        const staffCode = await verificationCode(staffEmail, 'PASSWORD_RESET');
        const nextStaffPassword = randomBytes(14).toString('base64url');
        await guest.save('/auth/forgot-password', {action:'reset', email:staffEmail, verificationCode:staffCode,
            password:nextStaffPassword, confirmPassword:nextStaffPassword});
        await redirectedToLogin(staff, allowed);
        await staff.login(staffEmail, nextStaffPassword);
        await staff.page(allowed);
        await admin.save('/admin/employees', {action:'status', id:staffId, status:'ON_LEAVE'});
        await redirectedToLogin(staff, allowed);
    }
    const managerRow = row(await admin.page('/admin/employees?keyword=' + encodeURIComponent(process.env.CRAVE_SMOKE_ADMIN_EMAIL)), process.env.CRAVE_SMOKE_ADMIN_EMAIL);
    const managerId = decode(/<small\b[^>]*>([\s\S]*?)<\/small>/i.exec(managerRow)?.[1] || '').trim();
    assert(managerId, 'Admin ID missing from employee row.');
    const selfLock = await admin.request('/admin/employees', {method:'POST', status:302, body:{action:'status', id:managerId, status:'ON_LEAVE'}});
    assert(alertText(await admin.follow(selfLock)), 'Admin was allowed to disable the active administrator.');

    // Leave real order pages for browser checks. Creating a pending online order does not create a gateway link.
    await customer.save('/cart/items', {action:'add', foodId, quantity:1});
    const onlineResult = await customer.request('/checkout', {method:'POST', status:302, body:{action:'placeOrder',
        fulfillmentType:'PICKUP', receiverName:account.fullName, receiverPhone:account.phone, paymentMethod:'BANK_TRANSFER'}});
    const onlineId = orderId(onlineResult);
    const onlinePage = await customer.follow(onlineResult);
    assert(forms(onlinePage).some(form => form.action.endsWith('/payments/payos/' + onlineId)), 'Online payment form missing.');
    await customer.request('/payments/payos/' + onlineId, {method:'POST', body:{}, status:403, originHeader:'https://untrusted.example'});
    const foreign = new Client(), foreignEmail = 'foreign-' + token + '@example.com';
    const foreignPassword = randomBytes(14).toString('base64url');
    await requestCode(guest, '/auth/register', {email:foreignEmail});
    const foreignCode = await verificationCode(foreignEmail, 'REGISTER');
    await guest.save('/auth/register', {action:'register', fullName:'Khách kiểm thử quyền sở hữu', email:foreignEmail,
        phone:phone(), password:foreignPassword, confirmPassword:foreignPassword, verificationCode:foreignCode});
    await foreign.login(foreignEmail, foreignPassword);
    await foreign.save('/cart/items', {action:'add', foodId, quantity:1});
    const foreignResult = await foreign.request('/checkout', {method:'POST', status:302, body:{action:'placeOrder',
        fulfillmentType:'PICKUP', receiverName:'Khách kiểm thử quyền sở hữu', receiverPhone:phone(), paymentMethod:'CASH'}});
    const foreignId = orderId(foreignResult);
    await customer.request('/orders/' + foreignId, {status:403});
    await admin.save('/admin/promotions', {action:'status', id:promotionId, status:'INACTIVE'});
    const afterAddressDelete = await customer.save('/customer/addresses', {action:'delete', id:addressId});
    assert(!afterAddressDelete.includes(addressId), 'Deleted address still appears in the server-rendered list.');
    await customer.save('/auth/logout', {});
    await redirectedToLogin(customer, '/customer/profile');

    const credentialFile = path.resolve(__dirname, '../../backend/target/runtime-customer.env');
    fs.mkdirSync(path.dirname(credentialFile), {recursive:true});
    assert(!/[\r\n]/.test(email + password), 'Smoke credentials must fit environment-file lines.');
    fs.writeFileSync(credentialFile, 'CRAVE_SMOKE_CUSTOMER_EMAIL=' + email + '\nCRAVE_SMOKE_CUSTOMER_PASSWORD=' + password
        + '\nCRAVE_SMOKE_CASH_ORDER_ID=' + pickupId + '\nCRAVE_SMOKE_ONLINE_ORDER_ID=' + onlineId
        + '\nCRAVE_SMOKE_FOREIGN_ORDER_ID=' + foreignId + '\n', {mode:0o600});
    console.log('Live HTTP smoke passed: ' + checks + ' requests; JSP/forms/OTP/CSRF/permissions/cart/order/payment/admin/report flows.');
    console.log('Created isolated smoke fixtures: ' + token + '; browser credentials written to backend/target/runtime-customer.env.');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
