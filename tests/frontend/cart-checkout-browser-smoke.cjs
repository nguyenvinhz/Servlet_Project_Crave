// Execute the actual cart/checkout JSP markup and scripts in Chrome with controlled APIs.
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const os = require('node:os');
const { spawn } = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', '/usr/bin/chromium'
].find(fs.existsSync);
if (!chrome) throw new Error('Set CHROME_PATH to Chrome or Edge.');
const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-cart-checkout-'));
const common = `
const calls = [], pendingQuotes = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const waitFor = async predicate => { for (let i = 0; i < 160; i++) { if (predicate()) return; await sleep(10); } throw new Error('Condition timed out'); };
const byId = id => document.getElementById(id);
const input = (id, value) => { byId(id).value = value; byId(id).dispatchEvent(new Event('input', {bubbles:true})); };
const reply = (data, status = 200) => ({ok:status < 400, status, json:async () => ({success:status < 400, data, error:status >= 400 ? {code:'INVALID_OPTION', message:'Tùy chọn đã ngừng bán'} : null})});
let failSave = false, deferSave = false, saveResolve, deferFood = false, foodResolve, deferQuotes = false, failQuote = false;
let autoResolve = false, deferResolve = false;
let reverseLocation = null, deferReverse = false;
let savedAddresses = [{addressLine:'12 Nguyễn Trãi, Quận 1, TP.HCM', isDefault:true}];
const pendingReverses = [];
let resolvedLocation = {latitude:10.87, longitude:106.76, label:'12 Nguyễn Trãi, Quận 1, Hồ Chí Minh', approximate:false};
const pendingResolves = [];
let quoteValue = {deliveryFee:0, deliverable:true, message:'Đã xác nhận điểm nhận hàng.'};
const food = {id:'MA01', name:'Cơm gà', price:45000, status:'AVAILABLE', options:[
    {id:'TC01', name:'Thêm trứng', optionType:'TOPPING', extraPrice:8000, status:'ACTIVE'},
    {id:'TC02', name:'Thêm thịt', optionType:'TOPPING', extraPrice:12000, status:'ACTIVE'},
    {id:'SZ01', name:'Lớn', optionType:'SIZE', extraPrice:10000, status:'ACTIVE'},
    {id:'SZ02', name:'Rất lớn', optionType:'SIZE', extraPrice:15000, status:'ACTIVE'}]};
let cart = {cartId:'GH01', totalItems:2, subtotal:106000, items:[{cartItemId:'CI01', foodId:'MA01', foodName:'Cơm gà', quantity:2,
    basePrice:45000, unitPrice:53000, lineTotal:106000, note:'Ít cay', options:[{optionId:'TC01', name:'Thêm trứng', optionType:'TOPPING', extraPrice:8000}]}]};
window.fetch = async (url, options = {}) => {
    const call = {path:new URL(url, location.origin).pathname, method:options.method || 'GET', body:options.body ? JSON.parse(options.body) : null}; calls.push(call);
    assert(options.credentials === 'same-origin', 'API omitted session credentials');
    if (call.path === '/crave/api/cart') return reply(cart);
    if (call.path === '/crave/api/foods/MA01') { if (deferFood) await new Promise(resolve => foodResolve = resolve); return reply(food); }
    if (call.path === '/crave/api/cart/items/update') {
        if (deferSave) await new Promise(resolve => saveResolve = resolve);
        if (failSave) return reply(null, 400);
        const item = cart.items[0];
        if (call.body.optionIds) item.options = food.options.filter(option => call.body.optionIds.includes(option.id)).map(option => ({...option, optionId:option.id}));
        item.quantity = call.body.quantity;
        if (call.body.note != null) item.note = call.body.note;
        item.unitPrice = item.basePrice + item.options.reduce((sum, option) => sum + option.extraPrice, 0);
        item.lineTotal = item.unitPrice * item.quantity; cart.subtotal = item.lineTotal; cart.totalItems = item.quantity;
        return reply(cart);
    }
    if (call.path === '/crave/api/promotions/validate') return reply({valid:cart.subtotal >= 100000, discountAmount:10000, promotion:{promotionId:'KM01'}});
    if (call.path === '/crave/api/profile') return reply({fullName:'Khách hàng', phone:'0901234567'});
    if (call.path === '/crave/api/addresses') return reply(savedAddresses);
    if (call.path === '/crave/api/delivery/policy') return reply({configured:true, store:{address:'99 Đường Số 9, Dĩ An', latitude:10.8925871, longitude:106.7637161}});
    if (call.path === '/crave/api/delivery/search') {
        if (deferResolve) return new Promise(resolve => pendingResolves.push(value => resolve(reply(Array.isArray(value) ? value : [value]))));
        return autoResolve ? reply([{...resolvedLocation}]) : reply(null,400);
    }
    if (call.path === '/crave/api/delivery/reverse') {
        if (deferReverse) return new Promise(resolve => pendingReverses.push(value => resolve(reply(value))));
        return reverseLocation ? reply({...call.body, label:reverseLocation, approximate:true}) : reply(null,400);
    }
    if (call.path === '/crave/api/delivery/quote') {
        if (deferQuotes) return new Promise(resolve => pendingQuotes.push(value => resolve(reply(value))));
        if (failQuote) throw new Error('Network unavailable');
        return reply({...quoteValue});
    }
    if (call.path === '/crave/api/orders') return reply(null, 400);
    throw new Error('Unexpected API ' + call.path);
};
const writes = () => calls.filter(call => call.path === '/crave/api/cart/items/update');
const quoteCalls = () => calls.filter(call => call.path === '/crave/api/delivery/quote');
const openEditor = async () => { await waitFor(() => document.querySelector('.btn-edit')); document.querySelector('.btn-edit').click(); await waitFor(() => !byId('cartEditorSave').disabled); };
const readyCheckout = async () => { await waitFor(() => !byId('findDeliveryAddressBtn').disabled && byId('receiverName').value); };
const clickMapPoint = point => window.smokeDeliveryMap.fire('click', {latlng: point ? L.latLng(point) : window.smokeDeliveryMap.getCenter()});
const confirmPoint = point => { clickMapPoint(point); byId('confirmDeliveryLocationBtn').click(); };
const pickup = () => { byId('fulfillmentType').value = 'PICKUP'; byId('fulfillmentType').dispatchEvent(new Event('change')); };
`;
const cases = [
    {name:'checkout-empty-address-can-pin-and-order-with-coordinate-fallback', page:'checkout', setup:'savedAddresses = [];', run:`
        await readyCheckout();
        assert(!byId('deliveryAddress').value, 'Fixture address is not empty');
        confirmPoint([10.885,106.775]); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('deliveryAddress').value === '10.885000, 106.775000', 'Pin did not fill coordinates immediately');
        byId('placeOrderBtn').click(); await waitFor(() => calls.some(call => call.path.endsWith('/orders')));
        const order = calls.find(call => call.path.endsWith('/orders')).body;
        assert(order.deliveryAddress === '10.885000, 106.775000' && order.deliveryLatitude === 10.885, 'Coordinate-only address did not reach checkout');
    `},
    {name:'checkout-reverse-name-updates-address-without-changing-pin-or-quote', page:'checkout',
        setup:"reverseLocation = '12 Đường Số 9, Dĩ An';", run:`
        await readyCheckout();
        confirmPoint([10.885,106.775]); await waitFor(() => !byId('placeOrderBtn').disabled && byId('deliveryAddress').value.includes('12 Đường Số 9'));
        assert(byId('deliveryAddress').value.includes('(10.885000, 106.775000)'), 'Nearest address hid the precise selected point');
        assert(quoteCalls().length === 1 && quoteCalls()[0].body.latitude === 10.885, 'Reverse lookup altered pin or requested another quote');
        assert(calls.filter(call => call.path.endsWith('/search')).length === 1, 'Programmatic address update started another forward search');
    `},
    {name:'checkout-stale-reverse-result-cannot-overwrite-later-pin-or-typed-address', page:'checkout', setup:'deferReverse = true;', run:`
        await readyCheckout();
        clickMapPoint([10.88,106.77]); clickMapPoint([10.89,106.78]);
        pendingReverses[0]({label:'Old address',latitude:10.88,longitude:106.77}); await sleep(10);
        assert(byId('deliveryAddress').value === '10.890000, 106.780000', 'Old reverse response overwrote new pin');
        input('deliveryAddress','227 Nguyễn Văn Cừ, TP.HCM');
        pendingReverses[1]({label:'Other old address',latitude:10.89,longitude:106.78}); await sleep(10);
        assert(byId('deliveryAddress').value === '227 Nguyễn Văn Cừ, TP.HCM', 'Reverse response overwrote user typing');
    `},
    {name:'checkout-ambiguous-search-shows-choices-without-selecting-first', page:'checkout', setup:'deferResolve = true;', run:`
        await waitFor(() => pendingResolves.length === 1);
        pendingResolves[0]([{latitude:10.85,longitude:106.77,label:'Trường Đại học, 1 Võ Văn Ngân',approximate:false},
            {latitude:10.851,longitude:106.755,label:'Ngân hàng, 1 Võ Văn Ngân',approximate:false}]);
        await waitFor(() => document.querySelectorAll('.delivery-search-result').length === 2);
        assert(quoteCalls().length === 0 && byId('placeOrderBtn').disabled, 'First ambiguous result was automatically accepted');
        document.querySelectorAll('.delivery-search-result')[1].click();
        assert(byId('deliveryAddress').value.includes('Ngân hàng') && !byId('confirmDeliveryLocationBtn').hidden, 'Chosen result did not update address and pin');
        byId('confirmDeliveryLocationBtn').click(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls()[0].body.longitude === 106.755, 'Selected candidate coordinates were not sent');
    `},
    {name:'checkout-pasted-coordinates-use-returned-point-and-normalized-text', page:'checkout', setup:'deferResolve = true;', run:`
        await waitFor(() => pendingResolves.length === 1);
        input('deliveryAddress','106.775; 10.885'); byId('deliveryAddress').dispatchEvent(new Event('change'));
        pendingResolves[1]({latitude:10.885,longitude:106.775,label:'10.885000, 106.775000',approximate:false});
        await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('deliveryAddress').value === '10.885000, 106.775000', 'Coordinates were not normalized in address field');
        assert(quoteCalls()[0].body.latitude === 10.885 && quoteCalls()[0].body.longitude === 106.775, 'Pasted coordinates were geocoded to a different point');
    `},
    {name:'checkout-manual-pin-needs-confirmation-and-can-override-search', page:'checkout',
        setup:"autoResolve = true; quoteValue = {deliveryFee:7000,deliverable:true,message:'Đã xác nhận điểm nhận hàng.'};",
        run:`
        await waitFor(() => !byId('placeOrderBtn').disabled);
        input('deliveryAddress','12 Nguyễn Trãi, Quận 1, Thành Phố Hồ Chí Minh');
        byId('findDeliveryAddressBtn').click();
        await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('findDeliveryAddressBtn').textContent === 'Tìm theo địa chỉ', 'Misleading pin-center action remains');
        assert(quoteCalls().every(call => call.body.latitude === resolvedLocation.latitude), 'Address search used store coordinates');
        const count = quoteCalls().length;
        clickMapPoint([10.8925871,106.7637161]); await sleep(20);
        assert(quoteCalls().length === count && byId('placeOrderBtn').disabled, 'Unconfirmed map click changed the quote or allowed ordering');
        assert(byId('summaryFee').textContent === 'Chưa xác định', 'Old fee remained after pin changed');
        assert(!byId('confirmDeliveryLocationBtn').hidden, 'Manual selection cannot be confirmed');
        quoteValue = {deliveryFee:4000,deliverable:true,message:'Đã cập nhật phí giao hàng.'};
        byId('confirmDeliveryLocationBtn').click(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls().at(-1).body.latitude === 10.8925871 && byId('summaryFee').textContent === '4.000 đ', 'Confirmed manual pin was blocked or replaced');
    `},
    {name:'checkout-confirmed-manual-pin-wins-over-pending-address-search', page:'checkout',
        setup:"autoResolve = true; deferResolve = true; quoteValue = {deliveryFee:7000,deliverable:true,message:'Đã xác nhận điểm nhận hàng.'};",
        run:`
        await waitFor(() => pendingResolves.length === 1);
        clickMapPoint([10.8925871,106.7637161]);
        assert(quoteCalls().length === 0 && byId('placeOrderBtn').disabled, 'Unconfirmed manual click bypassed pending lookup');
        byId('confirmDeliveryLocationBtn').click();
        await waitFor(() => !byId('placeOrderBtn').disabled);
        pendingResolves[0]({latitude:10.8527907,longitude:106.7725584,label:'1 Võ Văn Ngân',approximate:false});
        await sleep(30);
        assert(quoteCalls().length === 1 && quoteCalls()[0].body.latitude === 10.8925871, 'Late search replaced the manually confirmed point');
        assert(byId('matchedDeliveryAddress').textContent.includes('do bạn chọn'), 'Late search replaced manual selection label');
    `},
    {name:'checkout-auto-quotes-default-address', page:'checkout',
        setup:"autoResolve = true; quoteValue = {deliveryFee:7000,deliverable:true,message:'Đã xác nhận điểm nhận hàng.'};",
        run:`
        await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls().length === 1, 'Saved address was not quoted automatically');
        assert(byId('summaryFee').textContent === '7.000 đ' && byId('summaryTotal').textContent === '113.000 đ', 'Automatic quote was not displayed');
        assert(byId('matchedDeliveryAddress').textContent.includes('12 Nguyễn Trãi'), 'Resolved address was not shown for checking');
    `},
    {name:'checkout-dragged-pin-is-sent-to-quote-and-order', page:'checkout', setup:'autoResolve = true;', run:`
        await waitFor(() => !byId('placeOrderBtn').disabled);
        let marker;
        smokeDeliveryMap.eachLayer(layer => { if (layer instanceof L.Marker && layer.options.draggable) marker = layer; });
        assert(marker && marker.dragging.enabled(), 'Receiving pin is not draggable');
        marker.fire('dragstart');
        assert(byId('placeOrderBtn').disabled && byId('confirmDeliveryLocationBtn').disabled, 'Dragging retained a valid order');
        marker.setLatLng([10.88,106.78]); marker.fire('dragend');
        assert(!byId('confirmDeliveryLocationBtn').disabled && byId('summaryFee').textContent === 'Chưa xác định', 'Drop retained the old fee');
        byId('confirmDeliveryLocationBtn').click(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls().at(-1).body.latitude === 10.88 && quoteCalls().at(-1).body.longitude === 106.78, 'Quote used lookup coordinates instead of dragged pin');
        byId('placeOrderBtn').click(); await waitFor(() => calls.some(call => call.path === '/crave/api/orders'));
        const order = calls.find(call => call.path === '/crave/api/orders').body;
        assert(order.deliveryLatitude === 10.88 && order.deliveryLongitude === 106.78, 'Order omitted the confirmed pin');
    `},
    {name:'checkout-approximate-street-result-needs-confirmation', page:'checkout',
        setup:"autoResolve = true; resolvedLocation = {latitude:10.75,longitude:106.68,label:'Nguyễn Văn Cừ, Hồ Chí Minh',approximate:true};",
        run:`
        await readyCheckout();
        assert(quoteCalls().length === 0 && byId('placeOrderBtn').disabled, 'Approximate street midpoint was treated as a confirmed house');
        assert(!byId('confirmDeliveryLocationBtn').hidden, 'Approximate point cannot be corrected and confirmed');
        confirmPoint([10.88,106.77]); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls().at(-1).body.latitude === 10.88, 'Confirmed correction was ignored');
    `},
    {name:'checkout-manual-pin-cancels-debounced-lookup', page:'checkout', run:`
        await readyCheckout();
        input('deliveryAddress','8 Tràng Thi, Hoàn Kiếm, Hà Nội');
        const count = calls.filter(call => call.path.endsWith('/search')).length;
        confirmPoint([10.88,106.77]); await waitFor(() => !byId('placeOrderBtn').disabled);
        await sleep(1300);
        assert(calls.filter(call => call.path.endsWith('/search')).length === count, 'Debounced lookup replaced manual selection');
    `},
    {name:'checkout-device-location-needs-confirmation-and-wins-over-search', page:'checkout',
        setup:`deferResolve = true; let deviceLocationCallback, deviceLocationOptions;
            Object.defineProperty(navigator, 'geolocation', {value:{getCurrentPosition:(success, failure, options) => {
                deviceLocationCallback = success; deviceLocationOptions = options;
            }}});`,
        run:`
        await waitFor(() => pendingResolves.length === 1);
        byId('useMyLocationBtn').click();
        assert(deviceLocationOptions.enableHighAccuracy && deviceLocationOptions.maximumAge === 0, 'Device location can use a stale low-accuracy fix');
        pendingResolves[0]({latitude:10.85,longitude:106.77,label:'Old lookup',approximate:false});
        deviceLocationCallback({coords:{latitude:10.885,longitude:106.775}});
        assert(quoteCalls().length === 0 && byId('placeOrderBtn').disabled, 'Device point was accepted without confirmation');
        byId('confirmDeliveryLocationBtn').click(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(quoteCalls().length === 1 && quoteCalls()[0].body.latitude === 10.885, 'Address lookup replaced device selection');
    `},
    {name:'checkout-free-delivery-above-150k-before-voucher', page:'checkout',
        setup:"autoResolve = true; cart.subtotal = 159000; sessionStorage.setItem('crave_applied_voucher', JSON.stringify({code:'SAVE10'})); quoteValue = {deliveryFee:0,deliverable:true,message:'Đơn hàng được miễn phí giao hàng.'};",
        run:`
        await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('summaryFee').textContent === '0 đ', 'Eligible delivery was charged');
        assert(byId('summaryTotal').textContent === '149.000 đ', 'Voucher or free shipping total is incorrect');
        assert(byId('deliveryLocationStatus').textContent.includes('miễn phí'), 'Free shipping confirmation is missing');
    `},
    {name:'checkout-late-address-lookup-cannot-overwrite-new-address', page:'checkout',
        setup:"autoResolve = true;",
        run:`
        await waitFor(() => !byId('placeOrderBtn').disabled);
        deferResolve = true;
        input('deliveryAddress','1 Địa chỉ cũ'); byId('deliveryAddress').dispatchEvent(new Event('change'));
        input('deliveryAddress','2 Địa chỉ mới'); byId('deliveryAddress').dispatchEvent(new Event('change'));
        assert(byId('placeOrderBtn').disabled && pendingResolves.length === 2, 'Unresolved address did not block stale shipping');
        pendingResolves[1]({latitude:10.85,longitude:106.77,label:'Địa chỉ mới',approximate:false});
        await waitFor(() => !byId('placeOrderBtn').disabled);
        pendingResolves[0]({latitude:10.89,longitude:106.76,label:'Địa chỉ cũ',approximate:false});
        await sleep(20);
        assert(byId('matchedDeliveryAddress').textContent.includes('Địa chỉ mới') && !byId('matchedDeliveryAddress').textContent.includes('Địa chỉ cũ'), 'Stale lookup replaced latest address');
    `},
    {name:'cart-edit-options-prices-voucher', page:'cart', run:`
        await waitFor(() => document.querySelector('.btn-edit'));
        assert(byId('summaryDeliveryFee').textContent === 'Tính khi thanh toán', 'Cart charged an unquoted shipping fee');
        input('voucherCodeInput','SAVE10'); byId('voucherApplyBtn').click();
        await waitFor(() => byId('voucherAppliedChip').style.display === 'flex');
        await openEditor();
        assert(byId('cartItemEditor').open, 'Editor did not open');
        assert(document.querySelector('#cartEditorOptions input[value="TC01"]').checked, 'Selected topping was lost');
        assert(byId('cartEditorNote').value === 'Ít cay', 'Existing note was lost');
        document.querySelector('#cartEditorOptions input[value="TC02"]').click(); input('cartEditorQuantity','3'); input('cartEditorNote','Không hành');
        assert(byId('cartEditorTotal').textContent === '195.000 đ', 'Editor total omitted toppings or quantity');
        byId('cartEditorSave').click(); await waitFor(() => !byId('cartItemEditor').open);
        assert(writes().length === 1 && writes()[0].body.optionIds.join(',') === 'TC01,TC02', 'Wrong customization request');
        assert(byId('summarySubtotal').textContent === '195.000 đ' && byId('summaryTotal').textContent === '185.000 đ', 'Server cart/voucher total was not refreshed');
        assert(calls.filter(call => call.path.includes('/promotions/validate')).length === 2, 'Voucher was not revalidated');
    `},
    {name:'cart-cancel-clear-options-single-choice-and-mobile', page:'cart', mobile:true, run:`
        await openEditor();
        document.querySelector('#cartEditorOptions input[value="SZ01"]').click();
        document.querySelector('#cartEditorOptions input[value="SZ02"]').click();
        assert(!document.querySelector('#cartEditorOptions input[value="SZ01"]').checked, 'Size allowed multiple selections');
        byId('cartEditorCancel').click(); assert(!writes().length, 'Cancel mutated cart');
        await openEditor(); assert(!document.querySelector('#cartEditorOptions input[value="SZ02"]').checked, 'Cancelled selection leaked');
        document.querySelector('#cartEditorOptions input[value="TC01"]').click(); byId('cartEditorSave').click();
        await waitFor(() => !byId('cartItemEditor').open);
        assert(writes()[0].body.optionIds.length === 0, 'Removing all toppings did not send an empty list');
        assert(byId('summarySubtotal').textContent === '90.000 đ', 'Cleared topping remained charged');
        await openEditor();
        assert(byId('cartItemEditor').getBoundingClientRect().width <= innerWidth, 'Dialog overflowed mobile screen');
    `},
    {name:'cart-save-failure-double-submit-and-retry', page:'cart', run:`
        await openEditor(); failSave = true; deferSave = true; byId('cartEditorSave').click();
        byId('cartItemEditorForm').dispatchEvent(new Event('submit', {cancelable:true}));
        assert(byId('cartEditorSave').disabled && byId('cartEditorClose').disabled, 'Saving controls are not guarded');
        assert(writes().length === 1, 'Double submit produced duplicate writes');
        saveResolve(); await waitFor(() => !byId('cartEditorSave').disabled);
        assert(byId('cartItemEditor').open && byId('cartEditorStatus').textContent.includes('ngừng bán'), 'Save failure dismissed editor or hid the error');
        assert(byId('summarySubtotal').textContent === '106.000 đ', 'Failed edit changed the displayed cart');
        failSave = false; deferSave = false; byId('cartEditorSave').click(); await waitFor(() => !byId('cartItemEditor').open);
    `},
    {name:'cart-late-food-result-cannot-reopen-cancelled-editor', page:'cart', run:`
        await waitFor(() => document.querySelector('.btn-edit')); deferFood = true;
        document.querySelector('.btn-edit').click(); byId('cartEditorCancel').click(); foodResolve(); await sleep(30);
        assert(!byId('cartItemEditor').open && !writes().length, 'Late result reopened or saved the cancelled editor');
        assert(!byId('proceedCheckoutBtn').disabled, 'Cancel left checkout blocked');
    `},
    {name:'checkout-requires-pin-and-server-fee', page:'checkout', run:`
        await readyCheckout();
        assert(byId('placeOrderBtn').disabled, 'Delivery enabled before selecting a destination');
        assert(byId('summaryTotal').textContent.includes('+ phí giao hàng'), 'Unknown shipping displayed as zero');
        assert(!byId('deliveryFeeBands') && !/bán kính|Haversine|10 km/.test(document.body.innerText), 'Checkout published the pricing method');
        quoteValue = {deliveryFee:7000, deliverable:true, message:'Đã xác nhận điểm nhận hàng.'};
        confirmPoint(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('summaryFee').textContent === '7.000 đ' && byId('summaryTotal').textContent === '113.000 đ', 'Server quote was not used');
        assert(!/km|bán kính/.test(byId('deliveryLocationStatus').textContent), 'Quote status published the pricing method');
        byId('placeOrderBtn').click(); await waitFor(() => calls.some(call => call.path === '/crave/api/orders'));
        const payload = calls.find(call => call.path === '/crave/api/orders').body;
        assert(typeof payload.deliveryLatitude === 'number' && typeof payload.deliveryLongitude === 'number', 'Checkout omitted destination coordinates');
        assert(!('deliveryFee' in payload) && !('distanceKm' in payload), 'Checkout trusted client shipping calculations');
    `},
    {name:'checkout-address-invalidates-pin-outside-radius-and-pickup', page:'checkout', run:`
        await readyCheckout(); confirmPoint(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('summaryFee').textContent === '0 đ' && byId('summaryTotal').textContent === '106.000 đ', 'Free delivery did not allow checkout at the food total');
        input('deliveryAddress','Địa chỉ khác'); assert(byId('placeOrderBtn').disabled, 'Edited address kept an old quote');
        byId('deliveryAddress').dispatchEvent(new Event('change')); await waitFor(() => !byId('retryDeliveryQuoteBtn').hidden);
        quoteValue = {deliveryFee:null, deliverable:false, message:'Khu vực này hiện chưa được hỗ trợ giao hàng.'};
        confirmPoint(); await waitFor(() => byId('deliveryLocationStatus').textContent.includes('chưa được hỗ trợ'));
        assert(byId('placeOrderBtn').disabled, 'Outside-radius delivery was enabled');
        pickup(); assert(!byId('placeOrderBtn').disabled && byId('summaryFee').textContent === '0 đ', 'Pickup did not clear shipping restriction');
        byId('placeOrderBtn').click(); await waitFor(() => calls.some(call => call.path === '/crave/api/orders'));
        const payload = calls.find(call => call.path === '/crave/api/orders').body;
        assert(payload.deliveryLatitude === null && payload.deliveryLongitude === null && payload.deliveryAddress === null, 'Pickup leaked delivery location');
    `},
    {name:'checkout-late-quotes-and-network-retry', page:'checkout', run:`
        await readyCheckout(); deferQuotes = true;
        confirmPoint(); confirmPoint();
        assert(pendingQuotes.length === 2 && byId('placeOrderBtn').disabled, 'Pending quote did not block ordering');
        pendingQuotes[1]({deliveryFee:15000, deliverable:true, message:'Mới'}); await waitFor(() => !byId('placeOrderBtn').disabled);
        pendingQuotes[0]({deliveryFee:0, deliverable:true, message:'Cũ'}); await sleep(20);
        assert(byId('summaryFee').textContent === '15.000 đ', 'Late response overwrote the latest quote');
        deferQuotes = false; failQuote = true; confirmPoint(); await waitFor(() => !byId('retryDeliveryQuoteBtn').hidden);
        assert(byId('placeOrderBtn').disabled, 'Failed quote retained a usable price');
        failQuote = false; byId('retryDeliveryQuoteBtn').click(); await waitFor(() => !byId('placeOrderBtn').disabled);
        assert(byId('summaryFee').textContent === '0 đ', 'Quote retry failed');
    `}
];
let current;
const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://127.0.0.1').pathname;
    if (pathname.startsWith('/crave/assets/')) {
        const relative = pathname.slice('/crave/'.length);
        if (relative.includes('..')) { res.writeHead(400); res.end(); return; }
        const file = path.join(project, 'frontend', relative);
        if (!fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
        res.setHeader('Content-Type', file.endsWith('.js') ? 'text/javascript; charset=utf-8' : file.endsWith('.css') ? 'text/css; charset=utf-8' : 'image/svg+xml');
        res.end(fs.readFileSync(file)); return;
    }
    const view = current.page === 'cart' ? 'cart/index.jsp' : 'order/checkout.jsp';
    const markup = fs.readFileSync(path.join(project, 'frontend/WEB-INF/views', view), 'utf8')
        .replace(/<%@[^]*?%>/g, '').replace(/<c:set[^]*?\/>/g, '')
        .replace(/<c:url value='([^']*)'\s*\/>/g, (_, value) => '/crave' + value)
        .replace(/<c:out[^]*?\/>/g, '');
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crave/assets/css/styles.css"><link rel="stylesheet" href="/crave/assets/css/${current.page === 'cart' ? 'cart' : 'order'}.css"></head>
        <body class="crave-body" data-context-path="/crave"><script>${common}${current.setup || ''}</script>${markup}
        <script>if (window.L) { const createMap = L.map; L.map = (...args) => window.smokeDeliveryMap = createMap(...args); } document.addEventListener('DOMContentLoaded', async () => {
            const result = document.createElement('pre'); result.id = 'smoke-result';
            try { ${current.run} result.textContent = 'PASS ${current.name}'; }
            catch (error) { result.textContent = 'FAIL ' + error.stack; }
            document.body.append(result);
        });</script></body></html>`);
});
async function browser(url, index) {
    if (current.mobile) return mobileBrowser(url, index);
    return new Promise((resolve, reject) => {
        let output = '';
        const size = current.mobile ? '390,844' : '1280,1000';
        const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-extensions', '--no-first-run',
            '--host-resolver-rules=MAP tile.openstreetmap.org ~NOTFOUND', '--window-size=' + size,
            '--user-data-dir=' + path.join(artifacts, 'profile-' + index), '--dump-dom', '--virtual-time-budget=5000',
            '--screenshot=' + path.join(artifacts, current.name + '.png'), url], {windowsHide:true});
        child.stdout.on('data', chunk => output += chunk); child.stderr.resume();
        const timeout = setTimeout(() => { child.kill(); reject(new Error('Browser timed out')); }, 25000);
        child.on('error', error => {clearTimeout(timeout); reject(error);});
        child.on('close', () => {clearTimeout(timeout); resolve(output);});
    });
}
async function mobileBrowser(url, index) {
    const profile = path.join(artifacts, 'profile-' + index);
    const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--no-first-run',
        '--host-resolver-rules=MAP tile.openstreetmap.org ~NOTFOUND', '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank'], {windowsHide:true});
    child.stdout.resume(); child.stderr.resume();
    const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
    const timeout = setTimeout(() => child.kill(), 25000);
    let socket;
    try {
        const portFile = path.join(profile, 'DevToolsActivePort');
        for (let i = 0; i < 100 && !fs.existsSync(portFile); i++) await pause(50);
        const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
        const targets = await (await fetch('http://127.0.0.1:' + port + '/json')).json();
        socket = new WebSocket(targets.find(target => target.type === 'page').webSocketDebuggerUrl);
        await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
        let id = 0; const pending = new Map();
        socket.onmessage = event => {
            const message = JSON.parse(event.data);
            if (pending.has(message.id)) {pending.get(message.id)(message); pending.delete(message.id);}
        };
        const send = (method, params = {}) => new Promise((resolve, reject) => {
            const requestId = ++id;
            pending.set(requestId, message => message.error ? reject(new Error(message.error.message)) : resolve(message.result));
            socket.send(JSON.stringify({id:requestId, method, params}));
        });
        await send('Emulation.setDeviceMetricsOverride', {width:390, height:844, deviceScaleFactor:1, mobile:true});
        await send('Page.navigate', {url});
        for (let i = 0; i < 100; i++) {
            const result = await send('Runtime.evaluate', {expression:'Boolean(document.getElementById("smoke-result"))', returnByValue:true});
            if (result.result.value) break;
            await pause(50);
        }
        const screenshot = await send('Page.captureScreenshot', {format:'png', captureBeyondViewport:false});
        fs.writeFileSync(path.join(artifacts, current.name + '.png'), Buffer.from(screenshot.data, 'base64'));
        const html = await send('Runtime.evaluate', {expression:'document.documentElement.outerHTML', returnByValue:true});
        return html.result.value;
    } finally {clearTimeout(timeout); socket?.close(); child.kill();}
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const selected = cases.filter(test => !process.argv[2] || new RegExp(process.argv[2]).test(test.name));
    let failed = 0;
    for (let index = 0; index < selected.length; index++) {
        current = selected[index];
        const html = await browser(`http://127.0.0.1:${server.address().port}/crave/${current.page}`, index);
        fs.writeFileSync(path.join(artifacts, current.name + '.html'), html);
        const result = [...html.matchAll(/<pre id="smoke-result">([^]*?)<\/pre>/g)].at(-1)?.[1] || 'FAIL no browser result';
        console.log(result); if (!result.startsWith('PASS')) failed++;
    }
    server.close(); console.log(`Cart/checkout: ${selected.length - failed}/${selected.length} passed. Artifacts: ${artifacts}`);
    process.exitCode = failed ? 1 : 0;
})().catch(error => {console.error(error); server.close(); process.exitCode = 1;});
