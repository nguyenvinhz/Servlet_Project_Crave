// Node 22+ and Chrome/Edge. Exercises real customer/cart/menu scripts in a browser.
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
].find(candidate => fs.existsSync(candidate));
if (!chrome) throw new Error('Set CHROME_PATH to Chrome or Edge.');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-customer-browser-'));
const photo = color => 'data:image/svg+xml,' + encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="80" height="60"><rect width="80" height="60" fill="${color}"/></svg>`);
const gallery = `<div class="craving-tile-media category-photo" data-category-gallery><img src="${photo('orange')}" alt=""><span hidden data-photo-src="${photo('orange')}"></span><span hidden data-photo-src="${photo('green')}"></span></div>`;
const menuCard = `<article class="clean-food-card" data-food-id="F1"><a class="clean-card-image-wrap"><span class="cart-added-label" hidden></span></a><a href="/crave/menu/detail?id=F1" class="food-card-action" data-cart-add data-food-name="Món thử"><svg><path d="M12 5v14M5 12h14"></path></svg></a></article><p id="menuCartFeedback"></p>`;
function markup(page) {
    return fs.readFileSync(path.join(project, 'frontend/WEB-INF/views', page + '.jsp'), 'utf8')
        .replace(/<%@[^]*?%>/g, '').replace(/<c:set[^]*?\/>/g, '')
        .replace(/<c:url value='([^']*)'\s*\/>/g, (_, value) => '/crave' + value);
}
const common = `
const calls=[];
const assert=(condition,message)=>{if(!condition)throw new Error(message);};
const waitFor=async(predicate,message='Condition timed out')=>{for(let i=0;i<350;i++){if(predicate())return;await new Promise(resolve=>setTimeout(resolve,20));}throw new Error(message);};
const reply=(data,status=200)=>({status,ok:status<400,json:async()=>({success:status<400,data})});
const cartFor=quantity=>({cartId:'G1',items:quantity?[{cartItemId:'I1',foodId:'F1',foodName:'Món thử',imageUrl:'/crave/assets/images/hero-meal.jpg',quantity,unitPrice:15000}]:[],totalItems:quantity,subtotal:quantity*15000});
window.CONTEXT_PATH='/crave';window.confirm=()=>true;
window.fetch=async(url,options={})=>{const request={path:new URL(url,location.origin).pathname,method:options.method||'GET',body:options.body?JSON.parse(options.body):null};calls.push(request);return fixtureFetch(request);};
const badge=()=>document.querySelector('.cart-badge-count');
const signalFromOtherTab=()=>{const frame=document.createElement('iframe');frame.hidden=true;frame.srcdoc='<body><script src="/crave/assets/js/customer.js"><'+'/script><script>CraveCart.update({items:[],totalItems:0,subtotal:0});<'+'/script>';document.body.append(frame);return frame;};
const finish=value=>{const pre=document.createElement('pre');pre.id='smoke-result';pre.textContent=value;document.body.append(pre);};
`;
const cases = [
    { name: 'menu-quick-add-immediate-badge-and-card-state', html: menuCard, setup: `
        const fixtureFetch=async request=>request.path.includes('/api/foods/')?reply({options:[]}):reply(request.method==='POST'?cartFor(1):cartFor(0));`, run: `
        await waitFor(()=>calls.some(request=>request.path==='/crave/api/cart'));
        const add=document.querySelector('[data-cart-add]');add.click();add.click();
        await waitFor(()=>badge().textContent==='1');
        assert(calls.filter(request=>request.method==='POST').length===1,'Double click added two portions');
        assert(document.querySelector('.food-card-in-cart'),'Added food card has no selected state');
        assert(!document.querySelector('.cart-added-label').hidden,'Added food label is hidden');
        assert(document.querySelector('.header-cart-icon-btn').getAttribute('aria-label').includes('1 món'),'Cart accessible count is stale');
        CraveCart.update(cartFor(0));
        assert(badge().style.display==='none'&&!document.querySelector('.food-card-in-cart'),'Removed food stayed marked or badge is stale');
    ` },
    { name: 'menu-stale-read-cannot-overwrite-new-add', html: menuCard, setup: `
        let finishRead;const fixtureFetch=async request=>request.path==='/crave/api/cart'?new Promise(resolve=>{finishRead=()=>resolve(reply(cartFor(0)));}):reply(request.method==='POST'?cartFor(2):{options:[]});`, run: `
        await waitFor(()=>finishRead);document.querySelector('[data-cart-add]').click();
        await waitFor(()=>badge().textContent==='2');finishRead();await new Promise(resolve=>setTimeout(resolve,80));
        assert(badge().textContent==='2','An earlier GET overwrote a later mutation');
    ` },
    { name: 'cross-tab-cart-badge-and-food-state', html: menuCard, setup: `
        let quantity=1;const fixtureFetch=async()=>reply(cartFor(quantity));`, run: `
        await waitFor(()=>badge().textContent==='1');quantity=4;
        const peer=signalFromOtherTab();await waitFor(()=>badge().textContent==='4','Cross-tab storage change did not refresh cart');
        assert(document.querySelector('.cart-added-label').textContent.includes('4'),'Cross-tab card portion count is stale');
        assert(calls.filter(request=>request.path==='/crave/api/cart').length>=2,'Other tab did not fetch authenticated state');peer.remove();
    ` },
    { name: 'cross-tab-invalidation-during-read-refreshes-again', html: menuCard, setup: `
        let releaseRead,reads=0;const fixtureFetch=async()=>++reads===1?new Promise(resolve=>{releaseRead=()=>resolve(reply(cartFor(1)));}):reply(cartFor(4));`, run: `
        await waitFor(()=>releaseRead);window.dispatchEvent(new StorageEvent('storage',{key:'crave:cart-updated',newValue:'peer-change'}));
        releaseRead();await waitFor(()=>badge().textContent==='4','An invalidation during a pending read was lost');
        assert(reads===2,'Remote invalidation did not queue exactly one fresh read');
    ` },
    { name: 'menu-food-options-navigate-to-customization', html: menuCard, redirect: true, setup: `
        const fixtureFetch=async request=>reply(request.path.includes('/api/foods/')?{options:[{id:'O1'}]}:cartFor(0));`, run: `
        document.querySelector('[data-cart-add]').click();await waitFor(()=>calls.some(request=>request.path.includes('/api/foods/')));
        assert(!calls.some(request=>request.method==='POST'),'Food options were skipped by direct add');
    ` },
    { name: 'detail-add-updates-cart-status-without-navigation', scripts: ['menu-detail'], html: `
        <span id="basePriceDisplay" data-base-price="15000"></span><span id="totalPriceDisplay"></span>
        <form id="orderCustomizationForm" data-food-id="F1"><input id="orderQuantity" value="2"><p id="foodCartStatus" hidden></p><button id="addToCartBtn" type="button"><span>Thêm vào giỏ</span></button></form>`, setup: `
        const fixtureFetch=async request=>reply(request.method==='POST'?cartFor(2):cartFor(0));`, run: `
        await handleAddToCart();assert(badge().textContent==='2','Detail add did not update badge');
        assert(!document.getElementById('foodCartStatus').hidden&&document.getElementById('foodCartStatus').textContent.includes('2'),'Detail cart status absent');
        assert(document.getElementById('totalPriceDisplay').textContent.includes('\u00a0₫'),'Currency can wrap onto next line');
    ` },
    { name: 'cart-quantity-delete-and-cross-tab-render', page: 'cart/index', setup: `
        let quantity=2;const fixtureFetch=async request=>{if(request.path.endsWith('/update'))quantity=request.body.quantity;else if(request.method==='DELETE')quantity=0;return reply(cartFor(quantity));};`, run: `
        await waitFor(()=>document.querySelector('.btn-inc')&&!document.querySelector('.btn-inc').disabled);
        document.querySelector('.btn-inc').click();await waitFor(()=>badge().textContent==='3');
        assert(CraveCart.cart.totalItems===3,'Cart quantity mutation did not publish shared state');
        quantity=5;const peer=signalFromOtherTab();await waitFor(()=>document.querySelector('.cart-stepper-val').textContent==='5');peer.remove();
        assert(document.getElementById('summarySubtotal').textContent.includes('75.000'),'Cross-tab cart summary is stale');
        document.querySelector('.btn-remove').click();await waitFor(()=>badge().textContent==='0');
        assert(document.getElementById('cartEmptyState').style.display==='block','Deleted last item did not show empty cart');
    ` },
    { name: 'cart-initial-read-cannot-overwrite-cross-tab-refresh', page: 'cart/index', setup: `
        let releaseRead,reads=0;const fixtureFetch=async()=>++reads===1?new Promise(resolve=>{releaseRead=()=>resolve(reply(cartFor(1)));}):reply(cartFor(5));`, run: `
        await waitFor(()=>releaseRead);const peer=signalFromOtherTab();await waitFor(()=>badge().textContent==='5');
        releaseRead();await waitFor(()=>!document.querySelector('.btn-inc').disabled);
        assert(badge().textContent==='5'&&document.querySelector('.cart-stepper-val').textContent==='5','Old cart page GET overwrote another-tab refresh');peer.remove();
    ` },
    { name: 'orders-pagination-bounds-and-currency', page: 'order/orders', setup: `
        const fixtureFetch=async request=>reply(request.path==='/crave/api/orders'?Array.from({length:19},(_,i)=>({orderId:'D'+(i+1),orderedAt:'2026-10-12T08:00:00',status:'COMPLETED',totalAmount:15000})):cartFor(0));`, run: `
        await waitFor(()=>document.querySelectorAll('.order-card').length===8);
        const nav=document.getElementById('ordersPagination');assert(!nav.hidden,'Missing order pagination');
        nav.querySelector('[aria-label="Trang sau"]').click();assert(document.querySelector('.order-card h3').textContent.endsWith('D9'),'Second page did not move records');
        nav.querySelector('[aria-label="Trang 3"]').click();assert(document.querySelectorAll('.order-card').length===3,'Last page record count is wrong');
        assert(nav.querySelector('[aria-label="Trang sau"]').disabled,'Next button enabled past last page');
        assert(document.querySelector('.price').textContent.includes('\u00a0₫'),'Order currency can wrap');
    ` },
    { name: 'promotions-mobile-pagination-and-currency', page: 'promotions/index', viewport: 390, setup: `
        const fixtureFetch=async request=>reply(request.path.includes('/api/promotions')?Array.from({length:8},(_,i)=>({code:'SAVE'+i,name:'Ưu đãi '+i,discountType:'FIXED',discountValue:15000,minimumOrderValue:100000,description:'Giảm 15.000 đ trên đơn từ 100.000 đ'})):cartFor(0));`, run: `
        await waitFor(()=>document.querySelectorAll('.promo-ticket-card').length===6);
        const nav=document.getElementById('promosPagination');nav.querySelector('[aria-label="Trang sau"]').click();
        assert(document.querySelectorAll('.promo-ticket-card').length===2,'Promotion last page wrong');
        assert(document.querySelector('.promo-ticket-value').textContent.includes('\u00a0₫'),'Promo value currency can wrap');
        assert(document.querySelector('.promo-ticket-desc').textContent.includes('15.000\u00a0đ'),'Promo description currency can wrap');
        assert(document.documentElement.scrollWidth<=document.documentElement.clientWidth,'Promotion page overflows mobile viewport');
    ` },
    { name: 'checkout-payos-failure-keeps-saved-order-and-clears-cart', page: 'order/checkout', redirect: true, expectedRedirect: '/crave/orders/D1', setup: `
        const fixtureFetch=async request=>request.path.endsWith('/link')?reply(null,502):request.path==='/crave/api/orders'?reply({orderId:'D1'},201):reply(cartFor(2));`, run: `
        await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
        const values={receiverName:'Khách hàng',receiverPhone:'0901234567',deliveryAddress:'123 Nguyễn Huệ',paymentMethod:'BANK_TRANSFER'};
        Object.entries(values).forEach(([id,value])=>{document.getElementById(id).value=value;});
        document.getElementById('paymentMethod').dispatchEvent(new Event('change'));
        document.getElementById('checkoutForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        await waitFor(()=>calls.some(request=>request.path.endsWith('/link')));
        await waitFor(()=>document.getElementById('checkoutAlert').textContent.includes('thanh toán lại'));
        assert(document.getElementById('placeOrderBtn').disabled,'PayOS failure re-enabled order creation');
        assert(CraveCart.cart.totalItems===0&&badge().textContent==='0','Saved order did not clear live cart');
        document.getElementById('checkoutForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        assert(calls.filter(request=>request.path==='/crave/api/orders').length===1,'PayOS retry created duplicate order');
        sessionStorage.setItem('payos-fixture-verified','true');
    `, redirectCheck: `if(sessionStorage.getItem('payos-fixture-verified')!=='true')throw new Error('PayOS redirect skipped failure-state checks');` },
    { name: 'order-payos-return-does-not-trust-paid-query', page: 'order/order-detail', initialPath: '/crave/orders/D1?payos=return&status=PAID', setup: `
        const fixtureFetch=async request=>reply(request.path.endsWith('/sync')?{status:'PENDING'}:request.path==='/crave/api/orders/D1'?{orderId:'D1',orderedAt:'2026-10-12T08:00:00',status:'PENDING_CONFIRMATION',fulfillmentType:'PICKUP',totalAmount:30000,payment:{paymentId:'P1',method:'BANK_TRANSFER',status:'PENDING'}}:cartFor(0));`, run: `
        await waitFor(()=>document.getElementById('orderContent').textContent.includes('Thanh toán qua PayOS'));
        const syncCall=calls.find(request=>request.path.endsWith('/sync'));assert(syncCall?.method==='POST','PayOS return did not reconcile provider state');
        const content=document.getElementById('orderContent');
        assert(content.textContent.includes('Chờ thanh toán')&&!content.textContent.includes('Đã thanh toán'),'Paid URL marked an unverified payment successful');
        const pay=[...content.querySelectorAll('button')].find(button=>button.textContent==='Thanh toán qua PayOS');
        const sync=[...content.querySelectorAll('button')].find(button=>button.textContent==='Kiểm tra thanh toán');
        assert(pay&&!pay.disabled&&sync&&!sync.disabled,'Pending payment lost payment/reconcile actions');
        sync.click();await waitFor(()=>content.textContent.includes('Ngân hàng chưa ghi nhận'));
        assert(!pay.disabled&&!sync.disabled,'Pending sync did not restore actions');
    ` },
    { name: 'category-photos-rotate', html: gallery, setup: `const fixtureFetch=async()=>reply(cartFor(0));`, run: `
        const image=document.querySelector('[data-category-gallery] img');const original=image.src;
        await waitFor(()=>image.src!==original,'Category did not advance to another actual food image');
    ` },
    { name: 'category-respects-reduced-motion', html: gallery, setup: `
        window.matchMedia=()=>({matches:true});const fixtureFetch=async()=>reply(cartFor(0));`, run: `
        const image=document.querySelector('[data-category-gallery] img');const original=image.src;
        await new Promise(resolve=>setTimeout(resolve,4800));assert(image.src===original,'Reduced-motion preference ignored');
    ` }
];
const selected = cases.filter(test => !process.argv[2] || new RegExp(process.argv[2]).test(test.name));
let current;
const server = http.createServer((request, response) => {
    const asset = /^\/crave\/assets\/(js|css|fonts|images)\/([\w.-]+)$/.exec(request.url);
    if (asset) {
        const file = path.join(project, 'frontend/assets', asset[1], asset[2]);
        response.setHeader('Content-Type', asset[1] === 'js' ? 'text/javascript; charset=utf-8' : asset[1] === 'css' ? 'text/css; charset=utf-8' : asset[1] === 'fonts' ? 'font/woff2' : 'image/jpeg');
        response.end(fs.existsSync(file) ? fs.readFileSync(file) : '');return;
    }
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    if (current.redirect && request.url.startsWith(current.expectedRedirect || '/crave/menu/detail')) {
        response.end(`<body><script>try{${current.redirectCheck || ''}document.body.innerHTML='<pre id="smoke-result">PASS ${current.name}</pre>';}catch(error){document.body.innerHTML='<pre id="smoke-result">FAIL '+error.message+'</pre>';}</script>`);return;
    }
    const pageStyle = current.page?.startsWith('cart/') ? 'cart' : current.page?.startsWith('order/') ? 'order' : current.page?.startsWith('promotions/') ? 'promotions' : null;
    response.end(`<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="/crave/assets/css/fonts.css"><link rel="stylesheet" href="/crave/assets/css/styles.css"><link rel="stylesheet" href="/crave/assets/css/customer.css">${pageStyle ? `<link rel="stylesheet" href="/crave/assets/css/${pageStyle}.css">` : ''}</head><body class="crave-body ${pageStyle === 'order' ? 'order-page' : ''}" data-context-path="/crave" data-account-type="CUSTOMER"><a class="header-cart-icon-btn"><span class="cart-badge-count">0</span></a><form id="logoutForm"></form><script>${common}${current.setup}</script>${current.page ? markup(current.page) : current.html}${(current.scripts || []).map(script => `<script src="/crave/assets/js/${script}.js"></script>`).join('')}<script src="/crave/assets/js/customer.js"></script><script>document.addEventListener('DOMContentLoaded',async()=>{try{${current.run}${current.redirect ? '' : `finish('PASS ${current.name}');`}}catch(error){finish('FAIL '+error.message);}});</script></body></html>`);
});
async function browser(url, index) {
    const profileDirectory = path.join(work, 'profile-' + index);
    const child = spawn(chrome, ['--headless=new', '--no-sandbox', '--disable-gpu', '--disable-background-networking', '--disable-extensions', '--no-first-run', '--no-default-browser-check', '--window-size=' + (current.viewport || 1280) + ',900', '--user-data-dir=' + profileDirectory, '--remote-debugging-port=0', url], { windowsHide: true });
    child.stdout.resume();child.stderr.resume();
    let socket, sequence = 0, startupError;
    child.on('error', error => { startupError = error; });
    const pending = new Map();
    const send = (method, params = {}) => new Promise(resolve => {
        const id = ++sequence;
        const timer = setTimeout(() => { pending.delete(id);resolve({}); }, 2000);
        pending.set(id, data => { clearTimeout(timer);resolve(data); });
        socket.send(JSON.stringify({ id, method, params }));
    });
    const deadline = Date.now() + 20000;
    try {
        while (Date.now() < deadline) {
            if (startupError) throw startupError;
            if (!socket) {
                const portFile = path.join(profileDirectory, 'DevToolsActivePort');
                if (fs.existsSync(portFile)) {
                    try {
                        const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
                        const targets = await fetch('http://127.0.0.1:' + port + '/json').then(result => result.json());
                        const target = targets.find(target => target.type === 'page');
                        if (target) {
                            socket = new WebSocket(target.webSocketDebuggerUrl);
                            await new Promise(resolve => socket.addEventListener('open', resolve, { once: true }));
                            socket.addEventListener('message', event => { const data = JSON.parse(event.data);if (data.id) { pending.get(data.id)?.(data);pending.delete(data.id); } });
                        }
                    } catch (_) { }
                }
            }
            if (socket?.readyState === WebSocket.OPEN) {
                const result = await send('Runtime.evaluate', { expression: "document.getElementById('smoke-result') ? document.documentElement.outerHTML : null", returnByValue: true });
                if (result.result?.result?.value) return result.result.result.value;
            }
            await new Promise(resolve => setTimeout(resolve, 50));
        }
        throw new Error('Browser timeout: ' + current.name);
    } finally {
        if (socket?.readyState === WebSocket.OPEN) { socket.send(JSON.stringify({ id: ++sequence, method: 'Browser.close' }));socket.close(); }
        await new Promise(resolve => { if (child.exitCode !== null) return resolve();child.once('close', resolve);setTimeout(resolve, 1000); });
        if (child.exitCode === null) child.kill();
    }
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    let failed = 0;
    for (let i = 0; i < selected.length; i++) {
        current = selected[i];
        const output = await browser(`http://127.0.0.1:${server.address().port}${current.initialPath || '/crave/fixture'}`, i);
        fs.writeFileSync(path.join(work, current.name + '.html'), output);
        const result = [...output.matchAll(/<pre id="smoke-result">([^<]*)<\/pre>/g)].at(-1)?.[1] || 'FAIL no result';
        console.log(current.name + ': ' + result);if (!result.startsWith('PASS ')) failed++;
    }
    server.close();console.log(`Browser fixtures: ${selected.length - failed}/${selected.length} passed. Artifacts: ${work}`);process.exitCode = failed ? 1 : 0;
})().catch(error => { console.error(error);server.close();process.exitCode = 1; });
