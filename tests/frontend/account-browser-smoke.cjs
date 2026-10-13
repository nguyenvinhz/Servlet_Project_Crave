// Run with Node 22+ and Chrome/Edge; optionally set CHROME_PATH or pass a case-name regex.
// Uses real account/cart/checkout JSP markup and scripts with deterministic API fixtures.
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const chrome = process.env.CHROME_PATH || [
 'C:/Program Files/Google/Chrome/Application/chrome.exe',
 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
 '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'
].find(candidate => fs.existsSync(candidate));
if (!chrome) throw new Error('Set CHROME_PATH to a Chrome or Edge executable.');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-account-browser-'));
const common = `
const calls = [];
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const waitFor = async (predicate, message = 'Condition timed out') => {
  for (let i = 0; i < 100; i++) { if (predicate()) return; await new Promise(r => setTimeout(r, 20)); }
  throw new Error(message);
};
const reply = (status, data = null, error = null) => ({status, ok:status >= 200 && status < 300, json:async()=>({success:status < 400, data, error})});
const submit = (id) => document.getElementById(id).dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
const setFields = (values) => Object.entries(values).forEach(([id,value])=>{document.getElementById(id).value=value;});
const profile = {id:'KH01',accountType:'CUSTOMER',fullName:'Khach Hang',email:'khach@example.com',phone:'0901234567'};
window.fetch = async (url, options = {}) => {
  const request = {url:new URL(url, location.origin).pathname, method:options.method || 'GET', body:options.body === undefined ? undefined : JSON.parse(options.body), options};
  calls.push(request);
  return fixtureFetch(request);
};
const finish = (message) => { const pre=document.createElement('pre');pre.id='smoke-result';pre.textContent=message;document.body.append(pre); };
`;
const cases = [
 {name:'register-client-validation', page:'auth/register', setup:`const fixtureFetch = async()=>reply(201,profile);`, run:`
 setFields({fullName:'Nguyen Van A',email:'vinh@example.com',phone:'0901234567',password:'password1',confirmPassword:'password2'});
 submit('registerForm');
 assert(calls.length===0,'Mismatched password reached API');
 assert(document.getElementById('confirmPassword').getAttribute('aria-invalid')==='true','Missing confirmation error');
 assert(document.activeElement.id==='confirmPassword','Confirmation field not focused');
 setFields({fullName:'  ',confirmPassword:'password1'});submit('registerForm');
 assert(calls.length===0,'Blank trimmed name reached API');
 assert(document.getElementById('fullName-error'),'Missing name error');
 `},
 {name:'profile-load-save-and-field-error',page:'customer/profile',setup:`
 let writes=0;const fixtureFetch=async request=>{
 if(request.method==='GET') return reply(200,profile);
 writes++; if(writes===1) return reply(409,null,{message:'Email da ton tai',fieldErrors:{email:'Email trung'}});
 return reply(200,{...profile,...request.body});};`,run:`
 await waitFor(()=>!document.getElementById('profile-fields').disabled,'Profile did not load');
 assert(document.getElementById('email').value===profile.email,'Profile not populated');
  assert(document.querySelector('.account-sidebar a[href$="/customer/addresses"]'),'Customer profile sidebar omitted addresses');
 setFields({fullName:'  Nguyen Quang Vinh  ',email:'vinh@example.com'});submit('profileForm');
 await waitFor(()=>document.getElementById('email-error'),'Server fieldErrors not rendered');
 assert(document.activeElement.id==='email','Server field error not focused');
 assert(!document.querySelector('#profileForm button[type=submit]').disabled,'Submit still disabled after error');
 submit('profileForm'); await waitFor(()=>document.getElementById('form-success').textContent.length>0);
 assert(calls[2].method==='PUT','Profile method was not PUT');
 assert(calls[2].body.fullName==='Nguyen Quang Vinh','Profile name not trimmed');
 assert(document.getElementById('fullName').value==='Nguyen Quang Vinh','Saved profile not populated');
 assert(!document.getElementById('email-error'),'Old field error remained');
 assert(calls[2].options.credentials==='same-origin','Session credentials missing');
 `},
 {name:'employee-profile-readonly',page:'customer/profile',accountType:'EMPLOYEE',setup:`const fixtureFetch=async()=>reply(200,{...profile,id:'NV01',accountType:'EMPLOYEE',role:'ADMIN'});`,run:`
 await waitFor(()=>document.getElementById('profile-loading').hidden);
 assert(document.getElementById('profile-fields').disabled,'Employee can edit profile');
 assert(!document.getElementById('profile-readonly').hidden,'Employee explanation missing');
  assert(!document.querySelector('.account-sidebar a[href$="/customer/addresses"]'),'Employee profile sidebar exposes customer addresses');
 `},
 {name:'profile-load-retry',page:'customer/profile',setup:`let attempts=0;const fixtureFetch=async()=>++attempts===1?reply(503,null,{message:'Database unavailable'}):reply(200,profile);`,run:`
 await waitFor(()=>!document.getElementById('profile-retry').hidden);
 assert(document.getElementById('profile-fields').disabled,'Failed load enabled blank form');
 document.getElementById('profile-retry').click();
 await waitFor(()=>!document.getElementById('profile-fields').disabled);
 assert(document.getElementById('email').value===profile.email,'Retry did not load profile');
 `},
 {name:'address-crud-default-cancel-xss',page:'customer/addresses',setup:`
 let addresses=[{id:'DC01',addressLine:'First address',note:'old note',isDefault:false}];
 const fixtureFetch=async request=>{
 const id=request.url.split('/').pop();
 if(request.method==='GET') return reply(200,id==='addresses'?addresses.map(a=>({...a})):{...addresses.find(a=>a.id===id)});
 if(request.method==='POST'){const address={...request.body,id:'DC02'};addresses.push(address);return reply(201,address);}
 if(request.method==='PUT'){if(request.body.isDefault) addresses.forEach(a=>a.isDefault=false);const address=addresses.find(a=>a.id===id);Object.assign(address,request.body);return reply(200,{...address});}
 addresses=addresses.filter(a=>a.id!==id);return reply(204);};
 window.confirm=()=>true;`,run:`
 const list=document.getElementById('address-list');
 const button=(index,label)=>[...list.children[index].querySelectorAll('button')].find(b=>b.textContent===label);
 await waitFor(()=>list.children.length===1);
 setFields({addressLine:'<img src=x onerror=window.evil=1>',note:'<svg onload=window.evil=1>'});
 submit('addressForm');submit('addressForm');
 await waitFor(()=>list.children.length===2&&!document.querySelector('#addressForm button[type=submit]').disabled);
 assert(calls.filter(c=>c.method==='POST').length===1,'Double submit reached API');
 assert(calls.find(c=>c.method==='POST').body.isDefault===false,'Unchecked default is not boolean false');
 assert(!list.querySelector('img')&&!list.querySelector('script')&&!window.evil,'Server text became HTML');
 button(1,'Đặt mặc định').click();
 await waitFor(()=>list.children[1].querySelector('.address-badge')&&!document.getElementById('address-refresh').disabled);
 const defaultWrite=calls.find(c=>c.method==='PUT');
 assert(defaultWrite.body.addressLine==='<img src=x onerror=window.evil=1>'&&defaultWrite.body.isDefault===true,'Default changed address content or missing true');
 assert(calls.some(c=>c.method==='GET'&&c.url.endsWith('/DC02')),'Detail was not fetched');
 button(0,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&!document.getElementById('address-cancel').disabled);
 assert(document.getElementById('addressLine').value==='First address','Edit did not populate detail');
 document.getElementById('address-cancel').click();
 assert(document.getElementById('addressLine').value===''&&document.getElementById('address-cancel').hidden,'Cancel did not reset');
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&!document.getElementById('address-cancel').disabled);
 setFields({addressLine:'Updated address',note:'New note'});submit('addressForm');
 await waitFor(()=>list.children[1].querySelector('h3').textContent==='Updated address'&&!document.querySelector('#addressForm button[type=submit]').disabled);
 assert(calls.filter(c=>c.method==='PUT').length===2,'Edit did not PUT');
 button(1,'Xóa').click();await waitFor(()=>list.children.length===1&&!document.getElementById('address-refresh').disabled);
 assert(calls.some(c=>c.method==='DELETE'&&c.body===undefined),'Delete not sent without body');
 button(0,'Xóa').click();await waitFor(()=>list.children.length===0);
 assert(!document.getElementById('address-empty-state').hidden,'Empty state missing after delete');
 `},
 {name:'address-forbidden',page:'customer/addresses',setup:`const fixtureFetch=async()=>reply(403,null,{message:'Customer only'});`,run:`
 await waitFor(()=>document.getElementById('form-error').textContent==='Customer only');
 assert(document.getElementById('address-fields').disabled,'Forbidden form enabled');
 assert(document.getElementById('address-empty-state').hidden,'Forbidden incorrectly displayed empty');
 `},
 {name:'register-redirect',page:'auth/register',setup:`const fixtureFetch=async request=>reply(request.url.endsWith('/code')?200:201,request.url.endsWith('/code')?{resendAfterSeconds:60}:profile);`,run:`
 setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');
 await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345'});submit('registerForm');`, redirect:'/crave/auth/login?registered=1'},
 ...[
  ['login-external-redirect','https://evil.example/phishing','/crave/menu'],
  ['login-network-redirect','//evil.example/phishing','/crave/menu'],
  ['login-other-context','/other/customer/profile','/crave/menu'],
  ['login-backslash','/crave/\\evil.example','/crave/menu'],
  ['login-api-loop','/crave/api/profile','/crave/menu'],
  ['login-matrix-api','/crave/api;test/profile','/crave/menu'],
  ['login-encoded-slash','/crave/%2f%2fevil.example','/crave/menu'],
  ['login-encoded-backslash','/crave/%5cevil.example','/crave/menu'],
  ['login-auth-loop','/crave/auth/login','/crave/menu'],
  ['login-valid-return','/crave/menu?category=1','/crave/menu?category=1']
 ].map(([name,returnTo,redirect])=>({name,page:'auth/login',query:'returnTo='+encodeURIComponent(returnTo),setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`setFields({email:'a@example.com',password:'password1'});submit('loginForm');`,redirect})),
 ...[
  ['ADMIN', '/admin'], ['MENU_MANAGER', '/admin/menu'], ['ORDER_STAFF', '/admin/orders'],
  ['PROMOTION_MANAGER', '/admin/promotions'], ['HR_MANAGER', '/admin/employees']
 ].map(([role, destination]) => ({name:'login-role-'+role.toLowerCase(), page:'auth/login',
   setup:`const fixtureFetch=async()=>reply(200,{...profile,accountType:'EMPLOYEE',role:'${role}'});`,
   run:`setFields({email:'staff@example.com',password:'password1'});submit('loginForm');`, redirect:'/crave'+destination})),
 {name:'login-staff-rejects-customer-return',page:'auth/login',query:'returnTo=%2Fcrave%2Fcheckout',
  setup:`const fixtureFetch=async()=>reply(200,{...profile,accountType:'EMPLOYEE',role:'ORDER_STAFF'});`,
  run:`setFields({email:'staff@example.com',password:'password1'});submit('loginForm');`,redirect:'/crave/admin/orders'},
 {name:'login-customer-rejects-admin-return',page:'auth/login',query:'returnTo=%2Fcrave%2Fadmin%2Fmenu',
  setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`setFields({email:'a@example.com',password:'password1'});submit('loginForm');`,redirect:'/crave/menu'},
 {name:'session-expiry-redirect',page:'customer/profile',setup:`const fixtureFetch=async()=>reply(401,null,{message:'Unauthorized'});`,run:``,redirect:'/crave/auth/login?reason=session-expired&returnTo=%2Fcrave%2Fcustomer%2Fprofile'},
 {name:'logout-redirect',page:'customer/profile',setup:`const fixtureFetch=async r=>reply(200,r.method==='GET'?profile:{message:'Signed out'});`,run:`document.getElementById('logoutForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));`,redirect:'/crave/auth/login?loggedOut=1'}
];

// Regressions for refreshing while mutating and preserving address edit drafts.
const addressFixture = `
 let addresses=[{id:'DC01',addressLine:'First address',note:'First note',isDefault:true},
 {id:'DC02',addressLine:'Second address',note:'Second note',isDefault:false}];
 const fixtureFetch=async request=>{
 const id=request.url.split('/').pop();
 if(request.method==='GET') return reply(200,id==='addresses'?addresses.map(a=>({...a})):{...addresses.find(a=>a.id===id)});
 if(request.method==='PUT'){if(request.body.isDefault)addresses.forEach(a=>a.isDefault=false);const found=addresses.find(a=>a.id===id);Object.assign(found,request.body);return reply(200,{...found});}
 if(request.method==='DELETE'){addresses=addresses.filter(a=>a.id!==id);if(addresses.length&&!addresses.some(a=>a.isDefault))addresses[0].isDefault=true;return reply(204);}
 throw new Error('Unexpected request');};
 window.confirm=()=>true;
`;
const addressHelpers=`
 const list=document.getElementById('address-list');
 const button=(index,label)=>[...list.children[index].querySelectorAll('button')].find(b=>b.textContent===label);
 const idle=()=>!document.querySelector('#addressForm button[type=submit]').disabled&&!document.getElementById('address-refresh').disabled;
 await waitFor(()=>list.children.length===2&&idle());
`;
const cartFixture = `
 const cart={cartId:'GH01',subtotal:100000,totalItems:1,items:[
 {cartItemId:'CT01',foodName:'Burger',imageUrl:'/assets/images/fixture.svg',quantity:1,unitPrice:100000,options:[]}
 ]};
 window.confirm=()=>true;
`;
cases.push(
 {name:'cart-canonical-load-error-and-retry',page:'cart/index',route:'cart',context:'/cart-shop',setup:`
 let attempts=0;const fixtureFetch=async()=>++attempts===1?reply(503,null,{message:'Cart database unavailable'}):reply(200,{items:[],subtotal:0,totalItems:0});`,run:`
 await waitFor(()=>document.getElementById('cartErrorMsg').textContent==='Cart database unavailable');
 assert(calls[0].url==='/cart-shop/api/cart','Cart ignored explicit context path');
 assert(calls[0].options.credentials==='same-origin','Cart session credentials missing');
 document.getElementById('retryFetchCartBtn').click();
 await waitFor(()=>document.getElementById('cartEmptyState').style.display==='block');
 assert(document.getElementById('cartErrorState').style.display==='none','Retry did not clear API error');
 `},
 {name:'cart-mutations-and-voucher-canonical-errors',page:'cart/index',route:'cart',context:'/cart-shop',setup:cartFixture+`
 const fixtureFetch=async request=>{
 if(request.method==='GET'&&request.url.endsWith('/api/cart'))return reply(200,cart);
 if(request.url.endsWith('/items/update'))return reply(400,null,{message:'Quantity rejected'});
 if(request.url.endsWith('/clear'))return reply(503,null,{message:'Clear unavailable'});
 if(request.url.endsWith('/validate'))return reply(400,null,{message:'Voucher rejected'});
 if(request.url.endsWith('/active'))return reply(503,null,{message:'Promotions unavailable'});
 throw new Error('Unexpected cart request');};`,run:`
 await waitFor(()=>document.querySelector('.btn-inc'));
 assert(document.querySelector('.cart-item-image').getAttribute('src')==='/assets/images/fixture.svg','Cart ignored canonical imageUrl field');
 document.querySelector('.btn-inc').click();
 await waitFor(()=>document.querySelector('.crave-toast')?.textContent==='Quantity rejected');
 assert(calls.at(-1).body.cartItemId==='CT01'&&calls.at(-1).body.quantity===2,'Quantity request incorrect');
 document.getElementById('clearCartBtn').click();
 await waitFor(()=>[...document.querySelectorAll('.crave-toast')].some(t=>t.textContent==='Clear unavailable'));
 document.getElementById('voucherCodeInput').value=' invalid ';
 document.getElementById('voucherApplyBtn').click();
 await waitFor(()=>document.getElementById('voucherFeedback').textContent==='Voucher rejected');
 assert(calls.at(-1).body.code==='INVALID'&&calls.at(-1).body.subtotal===100000,'Voucher request incorrect');
 assert(!document.getElementById('voucherApplyBtn').disabled,'Voucher button remained disabled after API error');
 document.getElementById('viewPromosBtn').click();
 await waitFor(()=>document.getElementById('promosModalList').textContent==='Promotions unavailable');
 assert(calls.every(c=>c.url.startsWith('/cart-shop/api/')&&c.options.credentials==='same-origin'),'Cart requests escaped app context/session');
 `},
 {name:'cart-session-expiry-clears-checkout-voucher',page:'cart/index',route:'cart',context:'/cart-shop',query:'code=SAVE20',setup:`
 sessionStorage.setItem('crave_applied_voucher','old account voucher');
 const fixtureFetch=async()=>reply(401,null,{message:'Unauthorized'});`,run:'',
 redirect:'/cart-shop/auth/login?reason=session-expired&returnTo=%2Fcart-shop%2Fcart%3Fcode%3DSAVE20',
 redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Expired account voucher retained');`},
 {name:'cart-mutation-session-expiry-clears-private-ui',page:'cart/index',route:'cart',context:'/cart-shop',setup:cartFixture+`
 sessionStorage.setItem('crave_applied_voucher','old account voucher');
 const badge=document.createElement('span');badge.className='cart-badge-count';document.body.append(badge);
 const fixtureFetch=async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(request.url.endsWith('/validate'))return reply(200,{valid:true,discountAmount:10000});
 return reply(401,null,{message:'Unauthorized'});};`,run:`
 await waitFor(()=>document.querySelector('.btn-inc'));
 document.getElementById('voucherCodeInput').value='SAVE10';
 document.getElementById('voucherApplyBtn').click();
 await waitFor(()=>document.getElementById('voucherAppliedCode').textContent==='SAVE10');
 window.addEventListener('beforeunload',()=>sessionStorage.setItem('cart-expiry-snapshot',JSON.stringify({
 items:document.getElementById('cartItemsList').children.length,
 badge:document.querySelector('.cart-badge-count').textContent,
 contentHidden:document.getElementById('cartContentWrapper').style.display==='none',
 errorVisible:document.getElementById('cartErrorState').style.display==='block'
 })));
 document.querySelector('.btn-inc').click();`,
 redirect:'/cart-shop/auth/login?reason=session-expired&returnTo=%2Fcart-shop%2Fcart',
 redirectCheck:`
 const snapshot=JSON.parse(sessionStorage.getItem('cart-expiry-snapshot'));
 if(!snapshot||snapshot.items!==0||snapshot.badge!=='0'||!snapshot.contentHidden||!snapshot.errorVisible)throw new Error('Expired mutation retained private cart UI');
 if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Expired mutation retained stored voucher');
 sessionStorage.removeItem('cart-expiry-snapshot');`},
 {name:'cart-checkout-preserves-voucher-handoff',page:'cart/index',route:'cart',context:'/cart-shop',setup:cartFixture+`
 const fixtureFetch=async request=>request.method==='GET'?reply(200,cart):reply(200,{valid:true,discountAmount:10000});`,run:`
 await waitFor(()=>document.querySelector('.btn-inc'));
 document.getElementById('voucherCodeInput').value=' save10 ';
 document.getElementById('voucherApplyBtn').click();
 await waitFor(()=>document.getElementById('voucherAppliedCode').textContent==='SAVE10');
 assert(!document.getElementById('proceedCheckoutBtn').disabled,'Checkout button is disabled');
 document.getElementById('proceedCheckoutBtn').click();`,redirect:'/cart-shop/checkout',
 redirectCheck:`
 const voucher=JSON.parse(sessionStorage.getItem('crave_applied_voucher'));
 if(voucher?.code!=='SAVE10'||voucher.discountAmount!==10000)throw new Error('Checkout lost applied voucher handoff');`},
 {name:'logout-clears-checkout-voucher',page:'auth/login',setup:`
 sessionStorage.setItem('crave_applied_voucher','old account voucher');
 const fixtureFetch=async()=>reply(200,{message:'Signed out'});`,run:`submit('logoutForm');`,redirect:'/crave/auth/login?loggedOut=1',
 redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Signed out account voucher retained');`},
 {name:'login-clears-previous-checkout-voucher',page:'auth/login',query:'returnTo='+encodeURIComponent('/crave/checkout?code=SAVE10'),setup:`
  sessionStorage.setItem('crave_applied_voucher','previous account voucher');
  const fixtureFetch=async()=>reply(200,profile);`,run:`setFields({email:'a@example.com',password:'password1'});submit('loginForm');`,
  redirect:'/crave/checkout?code=SAVE10',
  redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Successful login retained previous account voucher');`},
 {name:'logout-storage-failure-still-redirects',page:'auth/login',setup:`
 Storage.prototype.removeItem=()=>{throw new Error('Storage blocked');};
 const fixtureFetch=async()=>reply(200,{message:'Signed out'});`,run:`submit('logoutForm');`,redirect:'/crave/auth/login?loggedOut=1'}
);
cases.unshift(
 {name:'refresh-syncs-pristine-edited-default-checkbox',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(0,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 setFields({addressLine:'Unsaved line',note:'Unsaved note'});
 addresses.forEach(a=>a.isDefault=a.id==='DC02');
 document.getElementById('address-refresh').click();await waitFor(()=>list.children[1].querySelector('.address-badge')&&idle());
 assert(document.getElementById('addressLine').value==='Unsaved line'&&document.getElementById('note').value==='Unsaved note','Refresh discarded text draft');
 assert(!document.getElementById('isDefault').checked,'Refresh retains stale default checkbox after server default changed');
 submit('addressForm');await waitFor(()=>document.getElementById('address-cancel').hidden&&idle());
 assert(addresses.find(a=>a.id==='DC02').isDefault&&!addresses.find(a=>a.id==='DC01').isDefault,'Saving refreshed text draft unexpectedly switched default back');
 `},
 {name:'delete-unrelated-keeps-unsaved-default-checkbox',page:'customer/addresses',setup:addressFixture+`
 addresses.push({id:'DC03',addressLine:'Third address',note:null,isDefault:false});
 `,run:`
 const list=document.getElementById('address-list');
 const button=(index,label)=>[...list.children[index].querySelectorAll('button')].find(b=>b.textContent===label);
 const idle=()=>!document.querySelector('#addressForm button[type=submit]').disabled&&!document.getElementById('address-refresh').disabled;
 await waitFor(()=>list.children.length===3&&idle());
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 document.getElementById('isDefault').checked=true;
 setFields({addressLine:'Unsaved line',note:'Unsaved note'});
 document.getElementById('address-refresh').click();await waitFor(idle);
 assert(document.getElementById('isDefault').checked,'Refresh discarded unsaved default checkbox');
 button(2,'Xóa').click();await waitFor(()=>list.children.length===2&&idle());
 assert(document.getElementById('addressLine').value==='Unsaved line'&&document.getElementById('note').value==='Unsaved note','Unrelated delete discarded text draft');
 assert(document.getElementById('isDefault').checked,'Unrelated delete discarded unsaved default checkbox');
 submit('addressForm');await waitFor(()=>document.getElementById('address-cancel').hidden&&idle());
 assert(addresses.find(a=>a.id==='DC02').isDefault&&!addresses.find(a=>a.id==='DC01').isDefault,'Saving draft did not apply retained default choice');
 `},
 {name:'forbidden-detail-clears-stale-address-ui-and-recovers',page:'customer/addresses',setup:`
 let forbidden=true;const fixtureFetch=async request=>request.url.endsWith('/addresses')?reply(200,[
 {id:'DC01',addressLine:'Private address',note:null,isDefault:true}
 ]):forbidden?reply(403,null,{message:'Customer only'}):reply(200,{id:'DC01',addressLine:'Private address',note:null,isDefault:true});
 `,run:`
 const list=document.getElementById('address-list');
 await waitFor(()=>list.children.length===1&&!document.getElementById('address-fields').disabled);
 [...list.querySelectorAll('button')].find(b=>b.textContent==='Sửa').click();
 await waitFor(()=>document.getElementById('form-error').textContent==='Customer only'&&!document.getElementById('address-refresh').disabled);
 assert(list.children.length===0&&document.getElementById('address-fields').disabled,'Forbidden detail keeps stale private address/actions and writable form');
 assert(document.getElementById('address-empty-state').hidden&&document.getElementById('address-cancel').hidden,'Forbidden detail retained empty/edit state');
 forbidden=false;document.getElementById('address-refresh').click();
 await waitFor(()=>list.children.length===1&&!document.getElementById('address-fields').disabled);
 [...list.querySelectorAll('button')].find(b=>b.textContent==='Sửa').click();
 await waitFor(()=>!document.getElementById('address-cancel').hidden&&!document.getElementById('address-cancel').disabled);
 assert(document.getElementById('addressLine').value==='Private address','Forbidden detail recovery did not restore edit capability');
 forbidden=true;setFields({addressLine:'Draft address'});submit('addressForm');
 await waitFor(()=>document.getElementById('form-error').textContent==='Customer only'&&!document.getElementById('addressForm').hasAttribute('aria-busy'));
 assert(list.children.length===0&&document.getElementById('address-fields').disabled,'Forbidden save retained stale addresses or restored writable fieldset');
 assert(document.getElementById('addressLine').value===''&&document.getElementById('address-cancel').hidden,'Forbidden save retained private edit draft');
 forbidden=false;document.getElementById('address-refresh').click();
 await waitFor(()=>list.children.length===1&&!document.getElementById('address-fields').disabled);
 `},
 {name:'refresh-serializes-address-mutations',page:'customer/addresses',setup: `
 let reads=0,release;
 const fixtureFetch=async request=>{
 if(request.method!=='GET')return reply(201,{id:'DC02',...request.body});
 const snapshot=[{id:'DC01',addressLine:'First address',note:null,isDefault:true}];
 if(++reads===2)return new Promise(resolve=>{release=()=>resolve(reply(200,snapshot));});
 return reply(200,snapshot);};`,run: `
 const list=document.getElementById('address-list');
 await waitFor(()=>list.children.length===1);
 document.getElementById('address-refresh').click();
 assert(document.querySelector('#addressForm button[type=submit]').disabled,'Slow refresh permits submitting a concurrent mutation');
 assert([...list.querySelectorAll('button')].every(b=>b.disabled),'Slow refresh leaves address actions enabled');
 submit('addressForm');
 assert(!calls.some(c=>c.method==='POST'),'Mutation sent during a list refresh');
 release();await waitFor(()=>!document.querySelector('#addressForm button[type=submit]').disabled);
 `},
 {name:'default-other-address-keeps-draft-and-syncs-checkbox',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(0,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 setFields({addressLine:'Unsaved line',note:'Unsaved note'});
 assert(document.getElementById('isDefault').checked,'Initial default edit not populated');
 button(1,'Đặt mặc định').click();await waitFor(()=>list.children[1].querySelector('.address-badge')&&idle());
 assert(!document.getElementById('isDefault').checked,'Edited address retains stale default checkbox after choosing another default');
 assert(document.getElementById('addressLine').value==='Unsaved line'&&document.getElementById('note').value==='Unsaved note','Choosing another default discarded draft');
 submit('addressForm');await waitFor(()=>document.getElementById('address-cancel').hidden&&idle());
 assert(addresses.find(a=>a.id==='DC02').isDefault&&!addresses.find(a=>a.id==='DC01').isDefault,'Saving draft unexpectedly switched default back');
 `},
 {name:'default-edited-address-preserves-draft',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 setFields({addressLine:'Unsaved line',note:'Unsaved note'});
 button(1,'Đặt mặc định').click();await waitFor(()=>list.children[1].querySelector('.address-badge')&&idle());
 assert(document.getElementById('addressLine').value==='Unsaved line'&&document.getElementById('note').value==='Unsaved note','Choosing default discarded unsaved address edits');
 assert(!document.getElementById('address-cancel').hidden&&document.getElementById('isDefault').checked,'Edited address did not retain edit mode or new default');
 `},
 {name:'delete-promotes-edited-default-without-discarding-draft',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 setFields({addressLine:'Unsaved promoted line',note:'Unsaved note'});
 button(0,'Xóa').click();await waitFor(()=>list.children.length===1&&idle());
 assert(document.getElementById('isDefault').checked,'Promoted edited address still has unchecked default');
 assert(document.getElementById('addressLine').value==='Unsaved promoted line','Promotion discarded edit draft');
 `},
 {name:'refresh-clears-deleted-edit-target',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 addresses=addresses.filter(a=>a.id!=='DC02');
 document.getElementById('address-refresh').click();await waitFor(()=>list.children.length===1&&idle());
 assert(document.getElementById('address-cancel').hidden,'Refresh retains an edit target that no longer exists');
 assert(document.getElementById('addressLine').value===''&&document.getElementById('form-error').textContent.length>0,'Missing edit target did not reset with feedback');
 `},
 {name:'address-delete-cancel-keeps-draft',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(1,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 setFields({addressLine:'Unsaved line',note:'Unsaved note'});window.confirm=()=>false;
 button(0,'Xóa').click();await waitFor(idle);
 assert(!calls.some(c=>c.method==='DELETE')&&list.children.length===2,'Canceling deletion still sent DELETE');
 assert(document.getElementById('addressLine').value==='Unsaved line'&&!document.getElementById('address-cancel').hidden,'Canceled deletion discarded edit draft');
 `},
 {name:'address-default-detail-failure-recovers',page:'customer/addresses',setup:`
 const fixtureFetch=async request=>request.url.endsWith('/addresses')?reply(200,[
 {id:'DC01',addressLine:'First address',isDefault:true},{id:'DC02',addressLine:'Second address',isDefault:false}
 ]):reply(404,null,{message:'Address not found'});`,run:addressHelpers+`
 button(1,'Đặt mặc định').click();await waitFor(()=>document.getElementById('form-error').textContent==='Address not found'&&idle());
 assert(!calls.some(c=>c.method==='PUT'),'Failed detail lookup still sent default update');
 assert(list.children[0].querySelector('.address-badge')&&!list.children[1].querySelector('.address-badge'),'Failed default request changed badge');
 assert([...list.querySelectorAll('button')].every(b=>!b.disabled),'Failed default request left controls disabled');
 `},
 {name:'address-server-validation-network-error-retry',page:'customer/addresses',setup:`
 let writes=0;const addresses=[];const fixtureFetch=async request=>{
 if(request.method==='GET')return reply(200,addresses.map(a=>({...a})));
 if(++writes===1)return reply(400,null,{message:'Invalid address',fieldErrors:{addressLine:'Invalid line',note:'Invalid note'}});
 if(writes===2)throw new Error('Offline');
 const saved={id:'DC01',...request.body,isDefault:true};addresses.push(saved);return reply(201,saved);};`,run:`
 await waitFor(()=>!document.getElementById('address-fields').disabled&&!document.querySelector('#addressForm button[type=submit]').disabled);
 setFields({addressLine:'Draft line',note:'Draft note'});submit('addressForm');
 await waitFor(()=>document.getElementById('addressLine-error'));
 assert(document.activeElement.id==='addressLine','Address server field error did not focus input');
 assert(document.getElementById('addressLine').value==='Draft line'&&document.getElementById('note').value==='Draft note','Validation error discarded draft');
 submit('addressForm');await waitFor(()=>document.getElementById('form-error').textContent.includes('kết nối')&&!document.querySelector('#addressForm button[type=submit]').disabled);
 assert(!document.getElementById('addressLine-error')&&document.getElementById('addressLine').value==='Draft line','Network error retained obsolete field errors or discarded draft');
 submit('addressForm');await waitFor(()=>document.getElementById('address-list').children.length===1&&!document.querySelector('#addressForm button[type=submit]').disabled);
 assert(document.getElementById('addressLine').value===''&&document.getElementById('form-error').hidden,'Successful retry did not reset form/error');
 `},
 {name:'forbidden-refresh-clears-stale-list',page:'customer/addresses',setup:`
 let forbidden=false;const fixtureFetch=async()=>forbidden?reply(403,null,{message:'Customer only'}):reply(200,[{id:'DC01',addressLine:'Private address',isDefault:true}]);`,run:`
 const list=document.getElementById('address-list');await waitFor(()=>list.children.length===1);
 forbidden=true;document.getElementById('address-refresh').click();
 await waitFor(()=>document.getElementById('form-error').textContent==='Customer only');
 assert(list.children.length===0,'Forbidden refresh leaves stale address actions visible');
 assert(document.getElementById('address-fields').disabled,'Forbidden refresh leaves form enabled');
 `}
);

// Day 3: account navigation, revoked access, loading and narrow viewport regressions.
cases.push(
 ...['auth/login','auth/register','customer/profile','customer/addresses'].map(page=>({
 name:'account-empty-messages-hidden-'+page.split('/').pop(),page,
 setup:`const fixtureFetch=async()=>reply(200,${page==='customer/addresses'?'[]':'profile'});`,run:`
 await waitFor(()=>!document.getElementById('profile-loading')||document.getElementById('profile-loading').hidden);
 assert(document.getElementById('form-error').hidden,'Empty error message is visible');
 assert(!document.getElementById('form-success')||document.getElementById('form-success').hidden,'Empty success message is visible');
 `})),
 ...['auth/login','auth/register'].map(page=>({name:'auth-switch-preserves-return-'+page.split('/').pop(),page,
 query:'returnTo='+encodeURIComponent('/crave/checkout?code=SAVE10'),
 setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`
 const link=document.querySelector('[data-auth-switch]');
 assert(new URL(link.href).searchParams.get('returnTo')==='/crave/checkout?code=SAVE10','Switching auth form lost checkout destination');
 `})),
 {name:'register-preserves-checkout-return',page:'auth/register',query:'returnTo='+encodeURIComponent('/crave/checkout?code=SAVE10'),
 setup:`const fixtureFetch=async request=>reply(request.url.endsWith('/code')?200:201,request.url.endsWith('/code')?{resendAfterSeconds:60}:profile);`,run:`
 setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');
 await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345'});submit('registerForm');`,
 redirect:'/crave/auth/login?registered=1&returnTo=%2Fcrave%2Fcheckout%3Fcode%3DSAVE10'},
 {name:'auth-switch-drops-external-return',page:'auth/login',query:'returnTo='+encodeURIComponent('https://evil.example'),
 setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`
 assert(!new URL(document.querySelector('[data-auth-switch]').href).searchParams.has('returnTo'),'Unsafe return propagated into registration');
 `},
 {name:'register-rejects-email-control',page:'auth/register',setup:`const fixtureFetch=async()=>reply(201,profile);`,run:`
 setFields({fullName:'Nguyen Van A',email:'a'+String.fromCharCode(0)+'@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');
 assert(calls.length===0&&document.getElementById('email-error'),'Email with a control reached API');
 `},
 {name:'profile-loading-serializes-retry-and-submit',page:'customer/profile',setup:`
 let release;const fixtureFetch=async()=>new Promise(resolve=>{release=()=>resolve(reply(200,profile));});`,run:`
 assert(document.getElementById('profileForm').getAttribute('aria-busy')==='true','Profile loading has no busy state');
 document.getElementById('profile-retry').click();submit('profileForm');
 assert(calls.length===1,'Loading profile permitted concurrent retry or write');
 release();await waitFor(()=>!document.getElementById('profileForm').hasAttribute('aria-busy'));
 assert(!document.getElementById('profile-fields').disabled&&document.getElementById('email').value===profile.email,'Profile did not restore after slow load');
 `},
 {name:'employee-disabled-profile-ignores-submit',page:'customer/profile',accountType:'EMPLOYEE',setup:`
 const fixtureFetch=async()=>reply(200,{...profile,id:'NV01',accountType:'EMPLOYEE'});`,run:`
 await waitFor(()=>document.getElementById('profile-loading').hidden&&!document.getElementById('profileForm').hasAttribute('aria-busy'));
 submit('profileForm');assert(calls.length===1,'Disabled employee profile sent a write');
 assert(!document.querySelector('.field-error'),'Disabled employee profile displays validation errors');
 `},
 {name:'profile-forbidden-save-clears-private-data-and-recovers',page:'customer/profile',setup:`
 const fixtureFetch=async request=>request.method==='GET'?reply(200,profile):reply(403,null,{message:'Permission revoked'});`,run:`
 await waitFor(()=>!document.getElementById('profile-fields').disabled&&!document.getElementById('profileForm').hasAttribute('aria-busy'));
 setFields({fullName:'Private draft'});submit('profileForm');
 await waitFor(()=>document.getElementById('form-error').textContent==='Permission revoked');
 assert(document.getElementById('profile-fields').disabled,'Revoked profile remains writable');
 assert(['fullName','email','phone'].every(id=>document.getElementById(id).value===''),'Revoked profile retains private values');
 assert(!document.getElementById('profile-retry').hidden,'Revoked profile has no recovery action');
 document.getElementById('profile-retry').click();
 await waitFor(()=>!document.getElementById('profile-fields').disabled&&!document.getElementById('profileForm').hasAttribute('aria-busy'));
 assert(document.getElementById('email').value===profile.email&&document.getElementById('form-error').hidden,'Retry did not restore authorized profile');
 `},
 {name:'address-edit-and-cancel-move-keyboard-focus',page:'customer/addresses',setup:addressFixture,run:addressHelpers+`
 button(0,'Sửa').click();await waitFor(()=>!document.getElementById('address-cancel').hidden&&idle());
 assert(document.activeElement.id==='addressLine','Edit did not move focus to address field');
 document.getElementById('address-cancel').click();
 assert(document.activeElement.id==='addressLine'&&document.getElementById('address-cancel').hidden,'Cancel did not restore focus to add form');
 `},
 {name:'profile-session-expiry-clears-checkout-voucher',page:'customer/profile',setup:`
 sessionStorage.setItem('crave_applied_voucher','private previous voucher');
 const fixtureFetch=async()=>reply(401,null,{message:'Unauthorized'});`,run:``,
 redirect:'/crave/auth/login?reason=session-expired&returnTo=%2Fcrave%2Fcustomer%2Fprofile',
 redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Expired account retained checkout voucher');`},
 ...[
 {page:'auth/login'}, {page:'auth/register'},
 {page:'customer/profile',accountType:'CUSTOMER'}, {page:'customer/addresses',accountType:'CUSTOMER'},
 {page:'customer/profile',accountType:'EMPLOYEE',role:'ADMIN'},
 {page:'customer/profile',accountType:'EMPLOYEE',role:'MENU_MANAGER'}
 ].map(({page,accountType,role})=>({
 name:'account-mobile-layout-'+page.split('/').pop()+(role?'-'+role.toLowerCase():''),page,viewport:320,realHeader:true,accountType,role,
 setup:`const fixtureFetch=async()=>reply(200,${page==='customer/addresses'?'[{id:"DC01",addressLine:"x".repeat(255),note:"y".repeat(255),isDefault:true}]':`{...profile,accountType:${JSON.stringify(accountType||'CUSTOMER')},role:${JSON.stringify(role||null)}}`});`,run:`
 await waitFor(()=>!document.getElementById('profile-loading')||document.getElementById('profile-loading').hidden);
 await waitFor(()=>!document.getElementById('address-loading')||document.getElementById('address-loading').hidden);
 // Chromium on Windows may clamp its outer window to 500px; an iframe gives an exact 320px CSS viewport.
 const frame=document.createElement('iframe');frame.style.cssText='width:320px;height:900px;border:0';
 const loaded=new Promise(resolve=>frame.addEventListener('load',resolve,{once:true}));
 const head=[...document.head.querySelectorAll('link[rel=stylesheet],style')].map(element=>element.outerHTML).join('');
 frame.srcdoc='<!doctype html><html><head>'+head+'<link rel="stylesheet" href="'+location.origin+'/crave/assets/css/account.css?v=20261013-palette2"></head><body class="crave-body">'+document.querySelector('header').outerHTML+document.querySelector('main').outerHTML+document.querySelector('footer').outerHTML+'</body></html>';
 document.body.append(frame);await loaded;
 const screen=frame.contentWindow,doc=frame.contentDocument,main=doc.querySelector('main');
 assert(screen.innerWidth===320,'Fixture did not use exact 320px viewport');
 assert(doc.querySelectorAll('header nav a').length===${accountType==='CUSTOMER'?3:2},'Fixture did not render expected role navigation');
 if(${accountType==='CUSTOMER'})assert(doc.querySelector('header nav a[href$="/orders"]'),'Customer navigation omitted order history');
 if(${Boolean(role)})assert(doc.querySelector('a[href$="/admin"]'),'Employee navigation omitted admin entry');
  if(${page==='customer/profile'})assert(Boolean(doc.querySelector('.account-sidebar a[href$="/customer/addresses"]'))===${accountType==='CUSTOMER'},'Profile sidebar disagrees with account type');
 if(${Boolean(role)})assert(screen.getComputedStyle(doc.querySelector('header nav')).flexWrap==='wrap','Staff navigation does not wrap onto rows');
 assert(main.getBoundingClientRect().right<=screen.innerWidth,'Account layout extends beyond viewport');
 assert(doc.documentElement.scrollWidth<=screen.innerWidth,'Full account document including header has horizontal overflow: '+doc.documentElement.scrollWidth+'px');
 assert(screen.getComputedStyle(main).gridTemplateColumns.split(' ').length===1,'Mobile account grid did not collapse');
 const input=doc.querySelector('main input:not([type=checkbox]),main textarea');
 assert(input.getBoundingClientRect().width>=180,'Mobile form input is too narrow');
 assert(doc.querySelector('main button[type=submit]').getBoundingClientRect().height>=44,'Submit target is too small');
 `}))
);

const voucherFixture = `
 const validVoucher={valid:true,originalSubtotal:100000,discountAmount:10000,promotion:{promotionId:'KM01',code:'SAVE10'}};
 const saveVoucher=()=>sessionStorage.setItem('crave_applied_voucher',JSON.stringify({code:' save10 ',promotionId:'FORGED',discountAmount:999999}));
 const orderFields=()=>setFields({receiverName:'  Khach Hang  ',receiverPhone:'0901234567',deliveryAddress:'  123 Street  ',customerNote:' note '});
`;
const checkoutFixture = `
 // Checkout loads recipient and saved-address data before validating a voucher.
 const withCheckoutAccount = fixture => async request => {
   if(request.method==='GET'&&request.url.endsWith('/api/profile'))return reply(200,profile);
   if(request.method==='GET'&&request.url.endsWith('/api/addresses'))return reply(200,[{id:'DC01',addressLine:'123 Street',note:null,isDefault:true}]);
   return fixture(request);
 };
 const checkoutCalls = endpoint => calls.filter(request=>request.url.endsWith('/api/'+endpoint));
`;
const cartReady = `await waitFor(()=>document.querySelector('.btn-inc')&&!document.getElementById('proceedCheckoutBtn').disabled);`;
const applyCartVoucher = `document.getElementById('voucherCodeInput').value='SAVE10';document.getElementById('voucherApplyBtn').click();await waitFor(()=>document.getElementById('voucherAppliedCode').textContent==='SAVE10'&&!document.getElementById('proceedCheckoutBtn').disabled);`;
cases.push(
 {name:'cart-pending-voucher-blocks-enter-mutations-checkout',page:'cart/index',route:'cart',setup:cartFixture+voucherFixture+`
 let release;const fixtureFetch=async request=>request.method==='GET'?reply(200,cart):new Promise(resolve=>{release=()=>resolve(reply(200,validVoucher));});`,run:cartReady+`
 document.getElementById('voucherCodeInput').value='SAVE10';document.getElementById('voucherApplyBtn').click();
 document.getElementById('voucherCodeInput').dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',bubbles:true}));
 document.querySelector('.btn-inc').click();document.getElementById('clearCartBtn').click();document.getElementById('proceedCheckoutBtn').click();
 assert(calls.length===2,'Pending voucher sent duplicate validation, mutation, or checkout');
 assert(document.getElementById('proceedCheckoutBtn').disabled,'Pending voucher allowed checkout');
 release();await waitFor(()=>!document.getElementById('proceedCheckoutBtn').disabled);
 assert(document.getElementById('voucherApplyBtn').textContent.includes('Áp dụng'),'Voucher button did not restore its label');
 `},
 {name:'cart-quantity-revalidation-serializes-and-updates-total',page:'cart/index',route:'cart',setup:cartFixture+voucherFixture+`
 let releaseUpdate,releaseValidate,validations=0;
 const fixtureFetch=async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(request.url.endsWith('/items/update'))return new Promise(resolve=>{releaseUpdate=()=>resolve(reply(200,{...cart,subtotal:250000,totalItems:2,items:[{...cart.items[0],quantity:2}]}));});
 if(++validations===1)return reply(200,validVoucher);
 return new Promise(resolve=>{releaseValidate=()=>resolve(reply(200,{...validVoucher,originalSubtotal:250000,discountAmount:25000}));});};`,run:cartReady+applyCartVoucher+`
 document.querySelector('.btn-inc').click();document.querySelector('.btn-inc').click();document.querySelector('.btn-remove').click();
 assert(calls.filter(c=>c.url.endsWith('/items/update')).length===1,'Pending mutation repeated or raced removal');
 releaseUpdate();await waitFor(()=>releaseValidate);
 assert(document.getElementById('summaryDiscountRow').style.display==='none','Old cart discount remained while revalidation pending');
 assert(document.getElementById('proceedCheckoutBtn').disabled,'Revalidation allowed checkout');
 document.getElementById('proceedCheckoutBtn').click();
 releaseValidate();await waitFor(()=>!document.getElementById('proceedCheckoutBtn').disabled);
 assert(calls.at(-1).body.subtotal===250000,'Revalidation used old subtotal');
 assert(document.getElementById('summaryTotal').textContent==='240.000 đ','New total or fixed delivery fee incorrect');
 assert(document.getElementById('summaryDeliveryFee').textContent==='15.000 đ','Cart invented free delivery above 200k');
 `},
 {name:'cart-remove-during-revalidation-ignores-stale-response',page:'cart/index',route:'cart',setup:cartFixture+voucherFixture+`
 let release,validations=0;const fixtureFetch=async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(request.url.endsWith('/items/update'))return reply(200,{...cart,subtotal:200000});
 if(++validations===1)return reply(200,validVoucher);
 return new Promise(resolve=>{release=()=>resolve(reply(200,{...validVoucher,discountAmount:20000}));});};`,run:cartReady+applyCartVoucher+`
 sessionStorage.setItem('crave_applied_voucher','old voucher');document.querySelector('.btn-inc').click();await waitFor(()=>release);
 document.getElementById('voucherRemoveBtn').click();release();await waitFor(()=>!document.getElementById('proceedCheckoutBtn').disabled);
 assert(document.getElementById('voucherAppliedChip').style.display==='none','Delayed validation restored removed voucher');
 assert(document.getElementById('summaryTotal').textContent==='215.000 đ','Removed voucher left stale discount');
 assert(sessionStorage.getItem('crave_applied_voucher')===null,'Removed voucher persisted for checkout');
 `},
 ...['network','minimum'].map(kind=>({name:'cart-revalidation-'+kind+'-clears-discount',page:'cart/index',route:'cart',setup:cartFixture+voucherFixture+`
 let validations=0;const fixtureFetch=async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(request.url.endsWith('/items/update'))return reply(200,{...cart,subtotal:50000});
 if(++validations===1)return reply(200,validVoucher);
 ${kind==='network'?"throw new Error('offline');":"return reply(400,null,{code:'PROMOTION_MIN_ORDER_NOT_MET',message:'Minimum not met'});"}};`,run:cartReady+applyCartVoucher+`
 saveVoucher();document.querySelector('.btn-inc').click();await waitFor(()=>!document.getElementById('proceedCheckoutBtn').disabled);
 assert(document.getElementById('voucherAppliedChip').style.display==='none','Failed validation kept voucher');
 assert(document.getElementById('summaryTotal').textContent==='65.000 đ','Failed validation kept old amount');
 assert(sessionStorage.getItem('crave_applied_voucher')===null,'Failed validation left checkout voucher');
 `})),
 {name:'cart-storage-unavailable-handoff-uses-code-query',page:'cart/index',route:'cart',context:'/cart-shop',setup:cartFixture+voucherFixture+`
 const fixtureFetch=async request=>request.method==='GET'?reply(200,cart):reply(200,validVoucher);
 Storage.prototype.setItem=()=>{throw new Error('Storage blocked');};`,run:cartReady+applyCartVoucher+`
 document.getElementById('proceedCheckoutBtn').click();`,redirect:'/cart-shop/checkout?voucher=SAVE10'},
 {name:'checkout-validates-handoff-and-pickup-totals',page:'order/checkout',route:'checkout',context:'/cart-shop',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();const fixtureFetch=withCheckoutAccount(async request=>{
 if(request.method==='GET')return reply(200,{...cart,subtotal:'150000'});
 if(request.url.endsWith('/validate'))return reply(200,{...validVoucher,originalSubtotal:150000,discountAmount:15000});
 return reply(400,null,{code:'VALIDATION_ERROR',message:'Order validation failed'});});`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 const validation=checkoutCalls('promotions/validate');
 assert(validation.length===1&&validation[0].body.code==='SAVE10'&&validation[0].body.subtotal===150000,'Checkout failed to revalidate handoff against fresh subtotal');
 assert(checkoutCalls('profile').length===1&&checkoutCalls('addresses').length===1,'Checkout did not load recipient/address data');
 assert(document.getElementById('receiverName').value===profile.fullName&&document.getElementById('receiverPhone').value===profile.phone,'Checkout did not populate recipient');
 assert(document.getElementById('deliveryAddress').value==='123 Street','Checkout did not populate saved default address');
 assert(document.getElementById('summaryTotal').textContent==='150.000 đ','Checkout trusted saved discount');
 assert(!document.getElementById('summaryDiscountRow').hidden,'Validated discount missing');
 document.getElementById('fulfillmentType').value='PICKUP';document.getElementById('fulfillmentType').dispatchEvent(new Event('change'));
 assert(document.getElementById('summaryTotal').textContent==='135.000 đ'&&!document.getElementById('deliveryAddress').required,'Pickup fee/address incorrect');
 orderFields();submit('checkoutForm');await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 const order=calls.find(c=>c.url.endsWith('/api/orders'));
 assert(order.body.promotionId==='KM01'&&order.body.deliveryAddress===null,'Checkout sent forged ID or pickup address');
 assert(!('discountAmount' in order.body)&&!('subtotal' in order.body),'Checkout submitted client money');
 assert(order.body.receiverName==='Khach Hang'&&order.body.customerNote==='note','Checkout fields not trimmed');
 assert(document.getElementById('checkoutAlert').textContent==='Order validation failed','Canonical order error missing');
 assert(calls.every(c=>c.url.startsWith('/cart-shop/api/')&&c.options.credentials==='same-origin'),'Checkout escaped context/session');
 `},
 {name:'checkout-loading-and-order-double-submit-success',page:'order/checkout',route:'checkout',context:'/cart-shop',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();let releaseLoad,releaseOrder;const fixtureFetch=withCheckoutAccount(async request=>{
 if(request.method==='GET')return new Promise(resolve=>{releaseLoad=()=>resolve(reply(200,cart));});
 if(request.url.endsWith('/validate'))return reply(200,validVoucher);
 return new Promise(resolve=>{releaseOrder=()=>resolve(reply(201,{orderId:'DH01'}));});});`,run:`
 submit('checkoutForm');document.getElementById('retryCheckoutBtn').click();assert(calls.length===1,'Pending cart allowed order or duplicate load');
 releaseLoad();await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 orderFields();submit('checkoutForm');submit('checkoutForm');
 assert(calls.filter(c=>c.url.endsWith('/api/orders')).length===1,'Double submit created two order requests');
 assert(document.getElementById('placeOrderBtn').disabled,'Pending order button enabled');releaseOrder();`,redirect:'/cart-shop/orders/DH01',
 redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Successful checkout retained voucher');`},
 {name:'checkout-voucher-network-failure-blocks-and-retries',page:'order/checkout',route:'checkout',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();let validations=0,release;const fixtureFetch=withCheckoutAccount(async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(++validations===1)return new Promise(resolve=>{release=()=>resolve(reply(503,null,{code:'DATABASE_ERROR',message:'Validation unavailable'}));});
 return reply(200,validVoucher);});`,run:`
 await waitFor(()=>release);submit('checkoutForm');
 assert(checkoutCalls('cart').length===1&&checkoutCalls('promotions/validate').length===1&&checkoutCalls('orders').length===0,'Pending voucher allowed checkout');
 release();await waitFor(()=>!document.getElementById('retryCheckoutBtn').hidden);
 assert(document.getElementById('placeOrderBtn').disabled&&sessionStorage.getItem('crave_applied_voucher'),'Validation failure allowed order or lost voucher intent');
 assert(document.getElementById('checkoutAlert').textContent==='Validation unavailable','Validation failure missing');
 document.getElementById('retryCheckoutBtn').click();await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 assert(document.getElementById('summaryTotal').textContent==='105.000 đ','Validation retry failed');
 `},
 {name:'checkout-expired-handoff-clears-voucher',page:'order/checkout',route:'checkout',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();const fixtureFetch=withCheckoutAccount(async request=>request.method==='GET'?reply(200,cart):reply(400,null,{code:'PROMOTION_EXPIRED',message:'Voucher expired'}));`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 assert(document.getElementById('checkoutAlert').textContent==='Voucher expired','Expired voucher was silently dropped');
 assert(document.getElementById('summaryTotal').textContent==='115.000 đ'&&document.getElementById('summaryDiscountRow').hidden,'Expired voucher left discounted total');
 assert(sessionStorage.getItem('crave_applied_voucher')===null,'Expired handoff retained');
 `},
 {name:'checkout-order-expiry-updates-total-before-retry',page:'order/checkout',route:'checkout',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();let orders=0;const fixtureFetch=withCheckoutAccount(async request=>{
 if(request.method==='GET')return reply(200,cart);
 if(request.url.endsWith('/validate'))return reply(200,validVoucher);
 return reply(400,null,++orders===1?{code:'PROMOTION_EXPIRED',message:'Expired while ordering'}:{code:'VALIDATION_ERROR',message:'Stop fixture'});});`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);orderFields();submit('checkoutForm');
 await waitFor(()=>document.getElementById('checkoutAlert').textContent==='Expired while ordering');
 assert(calls.filter(c=>c.url.endsWith('/api/orders')).length===1,'Expired voucher silently retried full-price order');
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 assert(document.getElementById('summaryTotal').textContent==='115.000 đ'&&sessionStorage.getItem('crave_applied_voucher')===null,'Order expiry retained discount');
 submit('checkoutForm');await waitFor(()=>calls.filter(c=>c.url.endsWith('/api/orders')).length===2);
 assert(calls.at(-1).body.promotionId===null,'Explicit retry sent expired promotion');
 `},
 {name:'checkout-minimum-rejection-refreshes-changed-cart-before-retry',page:'order/checkout',route:'checkout',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();let loads=0,releaseReload;const fixtureFetch=withCheckoutAccount(async request=>{
 if(request.method==='GET'){
 if(++loads===1)return reply(200,{...cart,subtotal:150000});
 return new Promise(resolve=>{releaseReload=()=>resolve(reply(200,{...cart,subtotal:80000}));});}
 if(request.url.endsWith('/validate'))return reply(200,{...validVoucher,originalSubtotal:150000,discountAmount:15000});
 return reply(400,null,{code:'PROMOTION_MIN_ORDER_NOT_MET',message:'Current cart is below minimum'});});`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);orderFields();submit('checkoutForm');
 await waitFor(()=>releaseReload);submit('checkoutForm');
 assert(calls.filter(c=>c.url.endsWith('/api/orders')).length===1&&document.getElementById('placeOrderBtn').disabled,'Stale cart allowed another order');
 releaseReload();await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 assert(document.getElementById('summarySubtotal').textContent==='80.000 đ'&&document.getElementById('summaryTotal').textContent==='95.000 đ','Rejected order kept obsolete cart subtotal');
 assert(document.getElementById('checkoutAlert').textContent==='Current cart is below minimum','Cart refresh swallowed rejection warning');
 `},
 ...['FOOD_NOT_AVAILABLE','INVALID_OPTION'].map(code=>({name:'checkout-'+code.toLowerCase()+'-retains-cart-recovery',page:'order/checkout',route:'checkout',setup:cartFixture+checkoutFixture+`
 const fixtureFetch=withCheckoutAccount(async request=>request.method==='GET'?reply(200,cart):reply(400,null,{code:'${code}',message:'Fix cart selection'}));`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);setFields({receiverName:'Khach',receiverPhone:'0901234567',deliveryAddress:'Street'});submit('checkoutForm');
 await waitFor(()=>checkoutCalls('cart').length===2&&!document.getElementById('retryCheckoutBtn').hidden);
 assert(document.getElementById('placeOrderBtn').disabled,'Rejected cart selection still allowed checkout');
 assert(document.querySelector('a[href="/crave/cart"]'),'Rejected cart selection omitted cart recovery link');
 assert(document.getElementById('checkoutAlert').textContent==='Fix cart selection','Rejected cart warning lost during refresh');
 assert(document.getElementById('receiverName').value==='Khach','Rejected cart selection lost recipient draft');
 `})),
 {name:'checkout-root-context-query-voucher-validates',page:'order/checkout',route:'checkout',context:'',query:'voucher=SAVE10',setup:cartFixture+voucherFixture+checkoutFixture+`
 Storage.prototype.getItem=()=>{throw new Error('Storage blocked');};
 const fixtureFetch=withCheckoutAccount(async request=>request.method==='GET'?reply(200,cart):reply(200,validVoucher));`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);
 const validation=checkoutCalls('promotions/validate');
 assert(calls[0].url==='/api/cart'&&validation.length===1&&validation[0].url==='/api/promotions/validate'&&validation[0].body.code==='SAVE10','Root context/query voucher was ignored');
 assert(document.getElementById('summaryTotal').textContent==='105.000 đ','Root-context validated voucher total incorrect');
 assert(document.querySelector('a[href="/cart"]'),'Root-context cart link incorrect');
 `},
 {name:'checkout-empty-cart-cannot-submit',page:'order/checkout',route:'checkout',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();const fixtureFetch=withCheckoutAccount(async()=>reply(200,{items:[],subtotal:0,totalItems:0}));`,run:`
 await waitFor(()=>!document.getElementById('retryCheckoutBtn').hidden);submit('checkoutForm');
 assert(calls.length===1&&document.getElementById('placeOrderBtn').disabled,'Empty checkout submitted');
 assert(document.getElementById('summaryTotal').textContent==='0 đ'&&sessionStorage.getItem('crave_applied_voucher')===null,'Empty cart kept amount or voucher');
 `},
 {name:'checkout-session-expiry-clears-private-form',page:'order/checkout',route:'checkout',context:'/cart-shop',setup:cartFixture+voucherFixture+checkoutFixture+`
 saveVoucher();const fixtureFetch=withCheckoutAccount(async request=>request.method==='GET'?reply(200,cart):request.url.endsWith('/validate')?reply(200,validVoucher):reply(401,null,{message:'Unauthorized'}));`,run:`
 await waitFor(()=>!document.getElementById('placeOrderBtn').disabled);orderFields();
 window.addEventListener('beforeunload',()=>sessionStorage.setItem('checkout-expiry-snapshot',JSON.stringify({
 name:document.getElementById('receiverName').value,total:document.getElementById('summaryTotal').textContent,disabled:document.getElementById('placeOrderBtn').disabled
 })));submit('checkoutForm');`,redirect:'/cart-shop/auth/login?reason=session-expired&returnTo=%2Fcart-shop%2Fcheckout',redirectCheck:`
 const snapshot=JSON.parse(sessionStorage.getItem('checkout-expiry-snapshot'));
 if(snapshot?.name!==''||snapshot.total!=='0 đ'||!snapshot.disabled)throw new Error('Expired checkout retained private form');
 if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Expired checkout retained voucher');
 `}
);

cases.push(
 {name:'order-pickup-timeline-completed',page:'order/order-detail',route:'orders/DH01',setup:`
 const fixtureFetch=async()=>reply(200,{orderId:'DH01',status:'COMPLETED',fulfillmentType:'PICKUP',orderedAt:'2026-10-12T08:00:00',receiverName:'Khach',receiverPhone:'0901234567',details:[],totalAmount:45000});`,run:`
 await waitFor(()=>document.querySelector('.stepper'));
 assert(document.querySelectorAll('.step').length===3,'Pickup incorrectly includes delivery step');
 assert(document.querySelector('.step.active .step-label').textContent==='Hoàn tất','Completed pickup did not reach final step');
 assert(document.querySelector('.step.active .step-icon').textContent==='3','Pickup final step has wrong number');
 `},
 {name:'order-delivery-timeline-preparing',page:'order/order-detail',route:'orders/DH01',setup:`
 const fixtureFetch=async()=>reply(200,{orderId:'DH01',status:'PREPARING',fulfillmentType:'DELIVERY',orderedAt:'2026-10-12T08:00:00',receiverName:'Khach',receiverPhone:'0901234567',details:[],totalAmount:60000});`,run:`
 await waitFor(()=>document.querySelector('.stepper'));
 assert(document.querySelectorAll('.step').length===4,'Delivery timeline is missing a step');
 assert(document.querySelector('.step.active .step-label').textContent==='Đang chuẩn bị','Preparing delivery highlights wrong status');
 `},
 {name:'order-server-error-is-escaped',page:'order/order-detail',route:'orders/DH01',setup:`
 const fixtureFetch=async()=>reply(500,null,{message:'<img src=x onerror=window.evil=1>'});`,run:`
 await waitFor(()=>document.getElementById('orderContent').textContent.includes('onerror'));
 assert(!document.getElementById('orderContent').querySelector('img')&&!window.evil,'Server error was inserted as executable HTML');
 `}
);
cases.push(
 {name:'otp-register-send-validation-cooldown',page:'auth/register',setup:`
 let release;const fixtureFetch=async request=>{assert(request.url==='/crave/api/auth/register/code','Registration bypassed email code');assert(JSON.stringify(request.body)===JSON.stringify({email:'a@example.com'}),'Code request sent unrelated registration data');return new Promise(resolve=>release=()=>resolve(reply(200,{expiresInSeconds:600,resendAfterSeconds:60})));};`,run:`
 setFields({fullName:'Nguyen Van A',email:' a@example.com ',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');submit('registerForm');
 await waitFor(()=>release);assert(calls.length===1,'Duplicate send reached API');assert(document.getElementById('email').disabled,'Sending left identity editable');release();
 await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 assert(document.getElementById('email').readOnly,'Verified target email remained editable');assert(document.getElementById('otp-resend').disabled,'Resend cooldown missing');
 assert(document.getElementById('otp-resend').textContent.includes('60s'),'Cooldown did not start at 60 seconds');
 document.getElementById('otp-resend').dispatchEvent(new Event('click'));assert(calls.length===1,'Cooldown resend reached API');
 setFields({verificationCode:'12A456'});submit('registerForm');assert(calls.length===1,'Non-digit code reached API');assert(document.getElementById('verificationCode-error'),'Code error missing');
 `},
 {name:'otp-register-wrong-code-safe-errors',page:'auth/register',setup:`
 const fixtureFetch=async request=>request.url.endsWith('/code')?reply(200,{resendAfterSeconds:60}):reply(422,null,{message:'Code invalid',fieldErrors:{verificationCode:'<img src=x onerror=window.evil=1>'}});`,run:`
 setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');
 await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345'});submit('registerForm');await waitFor(()=>document.getElementById('verificationCode-error'));
 assert(calls[1].body.verificationCode==='012345','Leading zero was discarded');assert(!document.getElementById('verificationCode-error').querySelector('img')&&!window.evil,'Code error became executable HTML');
 assert(document.activeElement.id==='verificationCode','Wrong code was not focused');assert(!document.getElementById('otp-submit').disabled,'Code failure left form blocked');assert(document.getElementById('password').value==='password1','Code failure erased registration draft');
 `},
 {name:'otp-register-success-return-and-password-spaces',page:'auth/register',query:'returnTo=%2Fcrave%2Fcheckout',setup:`
 const fixtureFetch=async request=>{if(request.url.endsWith('/code'))return reply(200,{resendAfterSeconds:60});assert(request.body.verificationCode==='012345','Missing confirmed code');assert(request.body.password===' password1 '&&request.body.confirmPassword===' password1 ','Password spaces changed');assert(request.body.email==='a@example.com','Registration email changed');return reply(201,profile);};`,run:`
 setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:' password1 ',confirmPassword:' password1 '});submit('registerForm');
 await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345'});window.addEventListener('beforeunload',()=>sessionStorage.setItem('otp-registration-writes',String(calls.filter(c=>c.url==='/crave/api/auth/register').length)));submit('registerForm');submit('registerForm');`,redirect:'/crave/auth/login?registered=1&returnTo=%2Fcrave%2Fcheckout',redirectCheck:`if(sessionStorage.getItem('otp-registration-writes')!=='1')throw Error('Duplicate final registration');`},
 {name:'otp-register-send-network-retry',page:'auth/register',setup:`
 let sends=0;const fixtureFetch=async request=>{assert(request.url.endsWith('/register/code'),'Network retry bypassed code request');if(++sends===1)throw Error('offline');return reply(200,{resendAfterSeconds:60});};`,run:`
 setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');
 await waitFor(()=>!document.getElementById('form-error').hidden);assert(document.getElementById('otp-fields').hidden,'Failed send enabled verification');assert(!document.getElementById('otp-submit').disabled,'Network failure blocked retry');
 submit('registerForm');await waitFor(()=>document.getElementById('registerForm').dataset.otpStage==='verify');assert(sends===2,'Retry did not send code');
 `},
 {name:'otp-reset-generic-send-and-client-validation',page:'auth/forgot-password',setup:`
 const fixtureFetch=async request=>{assert(request.url==='/crave/api/auth/password/reset/code','Reset sent verification too soon');assert(Object.keys(request.body).join(',')==='email','Reset send leaked password or account type');return reply(200,{resendAfterSeconds:60});};`,run:`
 setFields({email:'bad-email'});submit('resetPasswordForm');assert(calls.length===0&&document.getElementById('email-error'),'Invalid email reached reset API');
 setFields({email:'unknown@example.com'});submit('resetPasswordForm');await waitFor(()=>document.getElementById('resetPasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 assert(document.getElementById('form-success').textContent.includes('Nếu email thuộc tài khoản đang hoạt động'),'Reset message reveals account existence');
 setFields({verificationCode:'12345',password:'password1',confirmPassword:'password1'});submit('resetPasswordForm');assert(calls.length===1&&document.getElementById('verificationCode-error'),'Short code reached API');
 setFields({verificationCode:'012345',confirmPassword:'password2'});submit('resetPasswordForm');assert(calls.length===1&&document.getElementById('confirmPassword-error'),'Mismatched new passwords reached API');
 `},
 {name:'otp-reset-expired-code-resend',page:'auth/forgot-password',setup:`
 let elapsed=0;const baseTime=Date.now();Date.now=()=>baseTime+elapsed;
 const fixtureFetch=async request=>request.url.endsWith('/code')?reply(200,{resendAfterSeconds:60}):reply(400,null,{message:'Mã xác nhận đã hết hạn.',fieldErrors:{verificationCode:'Hãy yêu cầu mã mới.'}});`,run:`
 setFields({email:'a@example.com'});submit('resetPasswordForm');await waitFor(()=>document.getElementById('resetPasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345',password:'password1',confirmPassword:'password1'});submit('resetPasswordForm');await waitFor(()=>document.getElementById('verificationCode-error'));
 assert(!document.getElementById('otp-submit').disabled&&document.getElementById('otp-resend').disabled,'Expiry broke retry or cooldown');
 elapsed=61000;await waitFor(()=>!document.getElementById('otp-resend').disabled);document.getElementById('otp-resend').click();
 await waitFor(()=>calls.filter(c=>c.url.endsWith('/code')).length===2&&!document.getElementById('verificationCode').disabled);
 assert(document.getElementById('verificationCode').value===''&&document.getElementById('otp-resend').disabled,'Resend retained previous code or skipped cooldown');
 `},
 {name:'otp-reset-success-login-and-return',page:'auth/forgot-password',query:'returnTo=%2Fcrave%2Fcheckout',setup:`
 sessionStorage.setItem('crave_applied_voucher','private voucher');const fixtureFetch=async request=>{if(request.url.endsWith('/code'))return reply(200,{resendAfterSeconds:60});assert(JSON.stringify(request.body)===JSON.stringify({email:'staff@example.com',verificationCode:'012345',password:' password1 ',confirmPassword:' password1 '}),'Reset final contract or password whitespace wrong');return reply(200,{message:'Changed'});};`,run:`
 setFields({email:'staff@example.com'});submit('resetPasswordForm');await waitFor(()=>document.getElementById('resetPasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345',password:' password1 ',confirmPassword:' password1 '});submit('resetPasswordForm');submit('resetPasswordForm');`,redirect:'/crave/auth/login?passwordReset=1&returnTo=%2Fcrave%2Fcheckout',redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw Error('Reset retained old account voucher');`},
 {name:'otp-reset-email-edit-keeps-cooldown',page:'auth/forgot-password',setup:`
 const fixtureFetch=async()=>reply(200,{resendAfterSeconds:60});`,run:`
 setFields({email:'first@example.com'});submit('resetPasswordForm');await waitFor(()=>document.getElementById('resetPasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'123456'});document.getElementById('otp-edit').click();assert(!document.getElementById('email').readOnly&&document.getElementById('otp-fields').hidden,'Edit did not reset code stage');
 assert(document.getElementById('verificationCode').value==='','Old target code retained');setFields({email:'second@example.com'});submit('resetPasswordForm');assert(calls.length===1,'Changing email bypassed cooldown');
 `},
 ...['CUSTOMER','EMPLOYEE'].map(accountType=>({name:'otp-change-session-mask-'+accountType.toLowerCase(),page:'auth/change-password',accountType,role:accountType==='EMPLOYEE'?'ADMIN':undefined,realHeader:true,setup:`
 const fixtureFetch=async request=>{if(request.method==='GET'){assert(request.url==='/crave/api/auth/session','Change page did not read actual session');return reply(200,{...profile,accountType:'${accountType}',role:'ADMIN',email:'staff@example.com'});}assert(request.url==='/crave/api/auth/password/change/code','Unexpected change write');assert(JSON.stringify(request.body)===JSON.stringify({currentPassword:' password1 '}),'Change send accepted target email or changed password whitespace');return reply(200,{resendAfterSeconds:60});};`,run:`
 await waitFor(()=>!document.getElementById('password-change-fields').disabled);
 assert(document.getElementById('password-account').textContent.includes('st•••@example.com')&&!document.getElementById('password-account').textContent.includes('staff@'),'Actual session email was not masked');
 assert(!document.querySelector('#changePasswordForm [name=email]'),'Change page accepts a target email');assert(document.querySelector('a[href$="/auth/change-password"]'),'Signed-in header omitted password link');
 setFields({currentPassword:' password1 '});submit('changePasswordForm');await waitFor(()=>document.getElementById('changePasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 assert(document.getElementById('currentPassword').readOnly,'Sending identity did not lock');assert(calls.every(c=>c.options.credentials==='same-origin'),'Change requests lost session credentials');
 `})),
 {name:'otp-change-session-load-retry',page:'auth/change-password',accountType:'EMPLOYEE',setup:`
 let reads=0;const fixtureFetch=async request=>{assert(request.method==='GET','Failed session page accepted a code request');return ++reads===1?reply(503,null,{message:'Unavailable'}):reply(200,{...profile,accountType:'EMPLOYEE'});};`,run:`
 await waitFor(()=>!document.getElementById('password-session-retry').hidden);assert(document.getElementById('password-change-fields').disabled,'Failed session enabled change form');submit('changePasswordForm');assert(calls.length===1,'Disabled change form accepted submit');
 document.getElementById('password-session-retry').click();await waitFor(()=>!document.getElementById('password-change-fields').disabled);assert(reads===2,'Session retry missing');
 `},
 {name:'otp-change-wrong-current-password',page:'auth/change-password',accountType:'EMPLOYEE',setup:`
 const fixtureFetch=async request=>request.method==='GET'?reply(200,{...profile,accountType:'EMPLOYEE'}):reply(400,null,{message:'Mật khẩu hiện tại không đúng.',fieldErrors:{currentPassword:'Vui lòng kiểm tra mật khẩu hiện tại.'}});`,run:`
 await waitFor(()=>!document.getElementById('password-change-fields').disabled);setFields({currentPassword:'password1'});submit('changePasswordForm');await waitFor(()=>document.getElementById('currentPassword-error'));
 assert(document.getElementById('otp-fields').hidden&&!document.getElementById('currentPassword').disabled,'Wrong current password enabled verification or blocked retry');assert(document.activeElement.id==='currentPassword','Current password error was not focused');
 `},
 {name:'otp-change-admin-success-login',page:'auth/change-password',accountType:'EMPLOYEE',role:'ADMIN',setup:`
 sessionStorage.setItem('crave_applied_voucher','private voucher');const fixtureFetch=async request=>{if(request.method==='GET')return reply(200,{...profile,accountType:'EMPLOYEE',role:'ADMIN'});if(request.url.endsWith('/code'))return reply(200,{resendAfterSeconds:60});assert(JSON.stringify(request.body)===JSON.stringify({verificationCode:'012345',password:' password1 ',confirmPassword:' password1 '}),'Change verify accepted current password/target email or altered leading zero');return reply(200,{message:'Changed'});};`,run:`
 await waitFor(()=>!document.getElementById('password-change-fields').disabled);setFields({currentPassword:'old-password1'});submit('changePasswordForm');await waitFor(()=>document.getElementById('changePasswordForm').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 setFields({verificationCode:'012345',password:' password1 ',confirmPassword:' password1 '});window.addEventListener('beforeunload',()=>sessionStorage.setItem('otp-change-writes',String(calls.filter(c=>c.url==='/crave/api/auth/password/change').length)));submit('changePasswordForm');submit('changePasswordForm');`,redirect:'/crave/auth/login?passwordChanged=1',redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw Error('Password change retained old account voucher');if(sessionStorage.getItem('otp-change-writes')!=='1')throw Error('Duplicate password change');`},
 {name:'otp-change-expired-session-login',page:'auth/change-password',setup:`const fixtureFetch=async()=>reply(401,null,{message:'Unauthorized'});`,run:``,redirect:'/crave/auth/login?reason=session-expired'},
 {name:'otp-login-reset-success-and-forgot-return',page:'auth/login',query:'passwordReset=1&returnTo=%2Fcrave%2Fcheckout',setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`
 assert(!document.getElementById('form-success').hidden&&document.getElementById('form-success').textContent.includes('Mật khẩu đã được cập nhật'),'Password reset confirmation missing');
 const forgot=document.querySelector('a[href*="/auth/forgot-password"]');assert(forgot&&new URL(forgot.href).searchParams.get('returnTo')==='/crave/checkout','Forgot link lost safe returnTo');
 `},
 {name:'otp-employee-readonly-profile-has-password-link',page:'customer/profile',accountType:'EMPLOYEE',setup:`const fixtureFetch=async()=>reply(200,{...profile,accountType:'EMPLOYEE',role:'ADMIN'});`,run:`
 await waitFor(()=>document.getElementById('profile-loading').hidden);const link=document.querySelector('.account-sidebar a[href$="/auth/change-password"]');assert(link&&!link.closest('fieldset')&&document.getElementById('profile-fields').disabled,'Employee readonly profile blocks password change');
 `},
 ...[
  ['register','registerForm'],['forgot-password','resetPasswordForm'],['change-password','changePasswordForm']
 ].map(([page,form])=>({name:'otp-mobile-layout-'+page,page:'auth/'+page,viewport:390,realHeader:true,screenshot:true,accountType:page==='change-password'?'EMPLOYEE':undefined,role:page==='change-password'?'ADMIN':undefined,setup:`
 const fixtureFetch=async request=>reply(200,request.method==='GET'?{...profile,accountType:'EMPLOYEE',role:'ADMIN'}:{resendAfterSeconds:60});`,run:`
 await waitFor(()=>window.innerWidth===390,'Mobile device metrics were not applied');
 if('${page}'==='change-password'){await waitFor(()=>!document.getElementById('password-change-fields').disabled);setFields({currentPassword:'password1'});}
 else if('${page}'==='register')setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});
 else setFields({email:'a@example.com'});
 submit('${form}');await waitFor(()=>document.getElementById('${form}').dataset.otpStage==='verify'&&!document.getElementById('verificationCode').disabled);
 assert(document.documentElement.scrollWidth<=window.innerWidth,'OTP page overflows mobile viewport');
 for(const id of ['verificationCode','otp-submit','otp-resend','otp-edit']){const box=document.getElementById(id).getBoundingClientRect();assert(box.width>0&&box.left>=0&&box.right<=window.innerWidth,id+' escaped viewport');}
 assert(document.getElementById('verificationCode').getAttribute('autocomplete')==='one-time-code'&&document.getElementById('verificationCode').getAttribute('inputmode')==='numeric','Mobile code entry hints missing');
 `}))
);

const selectedCases = cases.filter(test => !process.argv[2] || new RegExp(process.argv[2]).test(test.name));
if (!selectedCases.length) throw new Error('No browser cases matched.');
let current;
function renderTags(source) {
 const accountType=current.accountType||(current.page.startsWith('customer/')?'CUSTOMER':null);
 const pageStyle=current.page.startsWith('cart/')?'cart':current.page.startsWith('order/')?'order':'account';
 return source.replace(/<c:set[^]*?\/>/g,'').replace(/<c:if test="\$\{([^}]+)\}">([^]*?)<\/c:if>/g,(_,condition,content)=>{
   if(condition==="sessionScope.currentUser.role == 'ADMIN' or sessionScope.currentUser.role == 'MENU_MANAGER'")return ['ADMIN','MENU_MANAGER'].includes(current.role)?content:'';
   if(condition==="sessionScope.currentUser.role == 'ADMIN' or sessionScope.currentUser.role == 'ORDER_STAFF'")return ['ADMIN','ORDER_STAFF'].includes(current.role)?content:'';
   if(condition==="sessionScope.currentUser.accountType == 'CUSTOMER'")return accountType==='CUSTOMER'?content:'';
   if(condition==="sessionScope.currentUser.accountType == 'EMPLOYEE'")return accountType==='EMPLOYEE'?content:'';
   if(condition==="sessionScope.currentUser.accountType != 'EMPLOYEE'")return accountType!=='EMPLOYEE'?content:'';
   if(condition==="sessionScope.currentUser.role == 'ADMIN'")return current.role==='ADMIN'?content:'';
    if(condition==="sessionScope.currentUser.role == 'MENU_MANAGER'")return current.role==='MENU_MANAGER'?content:'';
    if(condition==="sessionScope.currentUser.role == 'ORDER_STAFF'")return current.role==='ORDER_STAFF'?content:'';
    if(condition==="sessionScope.currentUser.role == 'HR_MANAGER'")return current.role==='HR_MANAGER'?content:'';
    if(condition==="sessionScope.currentUser.role == 'PROMOTION_MANAGER'")return current.role==='PROMOTION_MANAGER'?content:'';
    if(condition==='not empty pageStyle')return content;
   throw new Error('Unrecognized JSP condition: '+condition);
 })
 .replace(/\$\{pageStyle\}/g,pageStyle).replace(/\$\{pageBodyClass\}/g,current.page.startsWith('order/')?'order-page':'')
 .replace(/\$\{empty pageTitle[^}]*\}/g,'')
 .replace(/<%@[^]*?%>/g,'').replace(/<c:url value='([^']*)'\s*\/>/g,(_,p)=>(current.context??'/crave')+p)
 .replace(/<c:out value='\$\{pageContext.request.contextPath\}'\s*\/>/g,current.context??'/crave')
 .replace(/<c:out[^]*?\/>/g,'')
 .replace(/\$\{empty (?:error|message) \? 'hidden' : ''\}/g,'hidden');
}
function markup(page) {
 return renderTags(fs.readFileSync(path.join(project,'frontend/WEB-INF/views',page+'.jsp'),'utf8'));
}
function headerMarkup() {
 const context=current.context??'/crave';
 const source=fs.readFileSync(path.join(project,'frontend/WEB-INF/views/components/header.jspf'),'utf8')
 .replace(/<c:choose>\s*<c:when[^>]*>([^]*?)<\/c:when>\s*<c:otherwise>([^]*?)<\/c:otherwise>\s*<\/c:choose>/g,(_,signedIn,guest)=>current.accountType?signedIn:guest)
 .replace(/<c:out value='\$\{pageContext.request.contextPath\}'\s*\/>/g,context)
 .replace(/\$\{pageContext.request.contextPath\}/g,context)
 .replace(/\$\{pageTitle[^}]*\}/g,'')
 // Keep local/inline styles deterministic; external web fonts are outside this fixture's scope.
 .replace(/<link[^>]*href="https:\/\/[^>]*>/g,'');
 return renderTags(source);
}
function footerMarkup() {
 return renderTags(fs.readFileSync(path.join(project,'frontend/WEB-INF/views/components/footer.jspf'),'utf8')
 .replace(/<script[^]*?<\/script>/g,'').replace(/<\/body>|<\/html>/g,''));
}
const server=http.createServer((req,res)=>{
 const context=current.context??'/crave';
 const assetPath=new URL(req.url,'http://127.0.0.1').pathname;
 const script=/\/assets\/js\/(account|cart|checkout|orders|order-detail)\.js$/.exec(assetPath);
 if(script){res.setHeader('Content-Type','text/javascript; charset=utf-8');res.end(fs.readFileSync(path.join(project,'frontend/assets/js',script[1]+'.js')));return;}
 const stylesheet=/\/assets\/css\/(styles|account|cart|order)\.css$/.exec(assetPath);
 if(stylesheet){res.setHeader('Content-Type','text/css; charset=utf-8');res.end(fs.readFileSync(path.join(project,'frontend/assets/css',stylesheet[1]+'.css')));return;}
 if(req.url==='/assets/images/fixture.svg'){res.setHeader('Content-Type','image/svg+xml');res.end('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"></svg>');return;}
 res.setHeader('Content-Type','text/html; charset=utf-8');
 const expectedInitial=context+'/'+(current.route||current.page)+(current.query?'?'+current.query:'');
 if(req.url!==expectedInitial){
 if(req.url!==current.redirect){res.end(`<pre id="smoke-result">FAIL unexpected redirect ${req.url} expected ${current.redirect}</pre>`);return;}
 res.end(`<!doctype html><html><body><script>try{${current.redirectCheck||''}document.body.innerHTML='<pre id="smoke-result">PASS ${current.name}</pre>';}catch(e){document.body.innerHTML='<pre id="smoke-result">FAIL '+e.message+'</pre>';}</script></body></html>`);return;}
 const run=current.redirect?current.run:current.run+`finish('PASS ${current.name}');`;
 const pageStyle=current.page.startsWith('cart/')?'cart':current.page.startsWith('order/')?'order':'account';
 const shell=current.realHeader?headerMarkup():`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="${context}/assets/css/styles.css?v=20261013-palette2"><link rel="stylesheet" href="${context}/assets/css/${pageStyle}.css?v=20261013-auth1"></head><body class="crave-body ${current.page.startsWith('order/')?'order-page':''}" data-context-path="${context}">`;
 const footer=current.realHeader?footerMarkup():`<form id="logoutForm" action="${context}/api/auth/logout"><button type="submit">Logout</button></form><p id="logout-error"></p>`;
 res.end(`${shell}${markup(current.page)}${footer}
 <script>${common}${current.setup}</script><script src="${context}/assets/js/account.js"></script>
 <script>document.addEventListener('DOMContentLoaded',async()=>{try{${run}}catch(e){finish('FAIL '+e.message);}});</script></body></html>`);
});
async function browser(url,index){
 const profileDirectory=path.join(work,'profile-'+index);
 const child=spawn(chrome,['--headless=new','--no-sandbox','--disable-gpu','--disable-background-networking','--disable-extensions','--no-first-run','--no-default-browser-check','--window-size='+(current.viewport||1280)+',900','--user-data-dir='+profileDirectory,'--remote-debugging-port=0',url],{windowsHide:true});
 child.stdout.resume();child.stderr.resume();
 let startupError,socket,sequence=0;
 child.on('error',error=>startupError=error);
 const pending=new Map();
 const send=(method,params={})=>new Promise(resolve=>{
   const id=++sequence;
   const timer=setTimeout(()=>{pending.delete(id);resolve({});},2000);
   pending.set(id,data=>{clearTimeout(timer);resolve(data);});
   socket.send(JSON.stringify({id,method,params}));
 });
 const deadline=Date.now()+25000;
 try {
   while(Date.now()<deadline){
     if(startupError)throw startupError;
     if(!socket){
       const portFile=path.join(profileDirectory,'DevToolsActivePort');
       if(fs.existsSync(portFile)){
         let port,targets;
         try {
           port=fs.readFileSync(portFile,'utf8').split('\n')[0];
           if(!/^\d+$/.test(port))throw new Error('Debugging port not ready');
           targets=await fetch('http://127.0.0.1:'+port+'/json').then(response=>response.json());
         } catch (_) {
           // Windows briefly locks the file while Chrome publishes its debugging endpoint.
           await new Promise(resolve=>setTimeout(resolve,50));
           continue;
         }
         const target=targets.find(page=>page.type==='page'&&page.url.startsWith(new URL(url).origin));
         if(target){
           socket=new WebSocket(target.webSocketDebuggerUrl);
           await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
           socket.addEventListener('message',event=>{const data=JSON.parse(event.data);if(data.id){pending.get(data.id)?.(data);pending.delete(data.id);}});
           if(current.screenshot&&current.viewport){await send('Emulation.setDeviceMetricsOverride',{width:current.viewport,height:900,deviceScaleFactor:1,mobile:false});}
         }
       }
     }
     if(socket){
       // Explicitly wait for the fixture result; --dump-dom can stall across async checkout redirects.
       const response=await send('Runtime.evaluate',{expression:"document.getElementById('smoke-result') ? document.documentElement.outerHTML : null",returnByValue:true});
       const output=response.result?.result?.value;
       if(output){
         if(current.screenshot){await send('Runtime.evaluate',{expression:"window.scrollTo({top:0,behavior:'instant'})"});const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});if(shot.result?.data)fs.writeFileSync(path.join(work,current.name+'.png'),Buffer.from(shot.result.data,'base64'));}
         return output;
       }
     }
     await new Promise(resolve=>setTimeout(resolve,50));
   }
   throw new Error('Browser timeout: '+current.name);
 } finally {
   if(socket&&socket.readyState===WebSocket.OPEN){socket.send(JSON.stringify({id:++sequence,method:'Browser.close'}));socket.close();}
   await new Promise(resolve=>{if(child.exitCode!==null)return resolve();child.once('close',resolve);setTimeout(resolve,1000);});
   if(child.exitCode===null)child.kill();
 }
}
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 let failed=0;
 for(let i=0;i<selectedCases.length;i++){
 current=selectedCases[i];const url=`http://127.0.0.1:${server.address().port}${current.context??'/crave'}/${current.route||current.page}${current.query?'?'+current.query:''}`;
 const output=await browser(url,i);fs.writeFileSync(path.join(work,current.name+'.html'),output);
 const matches=[...output.matchAll(/<pre id="smoke-result">([^<]*)<\/pre>/g)];const result=matches.at(-1)?.[1]||'FAIL no result';
 console.log(current.name+': '+result);if(!result.startsWith('PASS '))failed++;
 }
 server.close();console.log(`Browser fixtures: ${selectedCases.length-failed}/${selectedCases.length} passed. Artifacts: ${work}`);process.exitCode=failed?1:0;
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
