// Run with Node and Chrome/Edge; optionally set CHROME_PATH or pass a case-name regex.
// Uses real account/cart JSP markup and scripts with deterministic API fixtures.
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
 {name:'employee-profile-readonly',page:'customer/profile',setup:`const fixtureFetch=async()=>reply(200,{...profile,id:'NV01',accountType:'EMPLOYEE',role:'ADMIN'});`,run:`
 await waitFor(()=>document.getElementById('profile-loading').hidden);
 assert(document.getElementById('profile-fields').disabled,'Employee can edit profile');
 assert(!document.getElementById('profile-readonly').hidden,'Employee explanation missing');
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
 {name:'register-redirect',page:'auth/register',setup:`const fixtureFetch=async()=>reply(201,profile);`,run:`setFields({fullName:'Nguyen Van A',email:'a@example.com',phone:'0901234567',password:'password1',confirmPassword:'password1'});submit('registerForm');`, redirect:'/crave/auth/login?registered=1'},
 ...[
  ['login-external-redirect','https://evil.example/phishing','/crave/customer/profile'],
  ['login-network-redirect','//evil.example/phishing','/crave/customer/profile'],
  ['login-other-context','/other/customer/profile','/crave/customer/profile'],
  ['login-backslash','/crave/\\evil.example','/crave/customer/profile'],
  ['login-api-loop','/crave/api/profile','/crave/customer/profile'],
  ['login-matrix-api','/crave/api;test/profile','/crave/customer/profile'],
  ['login-encoded-slash','/crave/%2f%2fevil.example','/crave/customer/profile'],
  ['login-encoded-backslash','/crave/%5cevil.example','/crave/customer/profile'],
  ['login-auth-loop','/crave/auth/login','/crave/customer/profile'],
  ['login-valid-return','/crave/menu?category=1','/crave/menu?category=1']
 ].map(([name,returnTo,redirect])=>({name,page:'auth/login',query:'returnTo='+encodeURIComponent(returnTo),setup:`const fixtureFetch=async()=>reply(200,profile);`,run:`setFields({email:'a@example.com',password:'password1'});submit('loginForm');`,redirect})),
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
 {cartItemId:'CT01',foodName:'Burger',foodImageUrl:'/assets/images/fixture.svg',quantity:1,unitPrice:100000,options:[]}
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
 {name:'logout-clears-checkout-voucher',page:'auth/login',setup:`
 sessionStorage.setItem('crave_applied_voucher','old account voucher');
 const fixtureFetch=async()=>reply(200,{message:'Signed out'});`,run:`submit('logoutForm');`,redirect:'/crave/auth/login?loggedOut=1',
 redirectCheck:`if(sessionStorage.getItem('crave_applied_voucher')!==null)throw new Error('Signed out account voucher retained');`},
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

const selectedCases = cases.filter(test => !process.argv[2] || new RegExp(process.argv[2]).test(test.name));
if (!selectedCases.length) throw new Error('No browser cases matched.');
let current;
function markup(page) {
 const file=path.join(project,'frontend/WEB-INF/views',page+'.jsp');
 return fs.readFileSync(file,'utf8').replace(/<%@[^]*?%>/g,'').replace(/<c:url value='([^']*)'\s*\/>/g,(_,p)=>(current.context||'/crave')+p)
 .replace(/<c:out[^]*?\/>/g,'').replace(/<\/?c:if[^>]*>/g,'');
}
const server=http.createServer((req,res)=>{
 const context=current.context||'/crave';
 const script=/\/assets\/js\/(account|cart)\.js$/.exec(req.url);
 if(script){res.setHeader('Content-Type','text/javascript; charset=utf-8');res.end(fs.readFileSync(path.join(project,'frontend/assets/js',script[1]+'.js')));return;}
 if(req.url.endsWith('/assets/css/cart.css')){res.setHeader('Content-Type','text/css; charset=utf-8');res.end(fs.readFileSync(path.join(project,'frontend/assets/css/cart.css')));return;}
 if(req.url==='/assets/images/fixture.svg'){res.setHeader('Content-Type','image/svg+xml');res.end('<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"></svg>');return;}
 res.setHeader('Content-Type','text/html; charset=utf-8');
 const expectedInitial=context+'/'+(current.route||current.page)+(current.query?'?'+current.query:'');
 if(req.url!==expectedInitial){
 if(req.url!==current.redirect){res.end(`<pre id="smoke-result">FAIL unexpected redirect ${req.url} expected ${current.redirect}</pre>`);return;}
 res.end(`<!doctype html><html><body><script>try{${current.redirectCheck||''}document.body.innerHTML='<pre id="smoke-result">PASS ${current.name}</pre>';}catch(e){document.body.innerHTML='<pre id="smoke-result">FAIL '+e.message+'</pre>';}</script></body></html>`);return;}
 const run=current.redirect?current.run:current.run+`finish('PASS ${current.name}');`;
 res.end(`<!doctype html><html><head><meta charset="utf-8"></head><body data-context-path="${context}">${markup(current.page)}
 <form id="logoutForm" action="${context}/api/auth/logout"><button type="submit">Logout</button></form><p id="logout-error"></p>
 <script>${common}${current.setup}</script><script src="${context}/assets/js/account.js"></script>
 <script>document.addEventListener('DOMContentLoaded',async()=>{try{${run}}catch(e){finish('FAIL '+e.message);}});</script></body></html>`);
});
async function browser(url,index){
 return await new Promise((resolve,reject)=>{
 const child=spawn(chrome,['--headless=new','--no-sandbox','--disable-gpu','--disable-background-networking','--disable-extensions','--no-first-run','--no-default-browser-check','--user-data-dir='+path.join(work,'profile-'+index),'--dump-dom','--virtual-time-budget=12000',url],{windowsHide:true});
 let output='';child.stdout.on('data',d=>output+=d);child.stderr.resume();child.on('error',reject);
 const timer=setTimeout(()=>{child.kill();reject(new Error('Browser timeout'));},25000);
 child.on('close',()=>{clearTimeout(timer);resolve(output);});
 });
}
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 let failed=0;
 for(let i=0;i<selectedCases.length;i++){
 current=selectedCases[i];const url=`http://127.0.0.1:${server.address().port}${current.context||'/crave'}/${current.route||current.page}${current.query?'?'+current.query:''}`;
 const output=await browser(url,i);fs.writeFileSync(path.join(work,current.name+'.html'),output);
 const matches=[...output.matchAll(/<pre id="smoke-result">([^<]*)<\/pre>/g)];const result=matches.at(-1)?.[1]||'FAIL no result';
 console.log(current.name+': '+result);if(!result.startsWith('PASS '))failed++;
 }
 server.close();console.log(`Browser fixtures: ${selectedCases.length-failed}/${selectedCases.length} passed. Artifacts: ${work}`);process.exitCode=failed?1:0;
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
