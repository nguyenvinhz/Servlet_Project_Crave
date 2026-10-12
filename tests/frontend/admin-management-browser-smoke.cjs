// Exercises actual admin JSP forms and scripts in Chrome with controlled API responses.
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const os = require('node:os');
const {spawn} = require('node:child_process');
const project = path.resolve(__dirname, '../..');
const chrome = process.env.CHROME_PATH || [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(file => fs.existsSync(file));
if (!chrome) throw new Error('Set CHROME_PATH to Chrome or Edge.');
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'crave-admin-browser-'));
const common = `
const calls = [];
const assert = (value, message) => { if (!value) throw new Error(message); };
const waitFor = async predicate => { for (let i=0;i<100;i++) {if(predicate())return;await new Promise(r=>setTimeout(r,20));}throw new Error('Condition timed out');};
const fields = values => Object.entries(values).forEach(([id,value])=>document.getElementById(id).value=value);
const submit = id => document.getElementById(id).dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
const findButton = text => [...document.querySelectorAll('button')].find(node=>node.textContent.trim()===text);
const openActions = scope => {
    const trigger=scope.querySelector('.admin-actions-trigger');trigger.scrollIntoView({block:'center'});trigger.click();
    const menu=document.querySelector('.admin-dropdown-menu:not([hidden])');
    assert(menu && trigger.getAttribute('aria-expanded')==='true','Action menu did not open');
    assert(menu.parentNode===document.body,'Action menu clipped by table container');
    const rect=menu.getBoundingClientRect();assert(rect.left>=0&&rect.right<=innerWidth&&rect.top>=0&&rect.bottom<=innerHeight,'Action menu outside viewport');
    return menu;
};
const rowAction = (scope,text) => [...openActions(scope).querySelectorAll('button')].find(button=>button.textContent.trim()===text).click();
window.fetch = async (url,options={}) => {
    const call={path:new URL(url,location.origin).pathname,method:options.method||'GET',body:options.body?JSON.parse(options.body):undefined};
    calls.push(call);const data=fixture(call);
    return {status:200,ok:true,json:async()=>({success:true,data})};
};
`;
const cases = [
    {page:'customers',role:'ADMIN',name:'customer-search-lock-safe-text',setup:`
        let customers=[{id:'KH01',fullName:'Nguyễn <img src=x onerror=alert(1)>',email:'nguyen@example.com',phone:'0987654321',status:'ACTIVE',registeredAt:'2026-10-01T10:00:00',orderCount:2,totalSpent:50000},
            {id:'KH02',fullName:'Khách khác',email:'other@example.com',phone:'0987654322',status:'LOCKED',registeredAt:'2026-10-01T10:00:00',orderCount:0,totalSpent:0}];
        const fixture=call=>{if(call.method==='GET')return customers;assert(call.path==='/crave/api/admin/customers/KH01/status','Wrong customer path');assert(call.body.status==='LOCKED','Wrong customer status');customers[0]={...customers[0],status:call.body.status};return customers[0];};
        `,run:`
        await waitFor(()=>document.getElementById('managementTotal').textContent==='2');
        assert(!document.querySelector('#managementRows img'),'Customer name inserted as HTML');
        fields({managementSearch:'nguyen'});document.getElementById('managementSearch').dispatchEvent(new Event('input'));
        await waitFor(()=>document.querySelectorAll('#managementRows tr').length===1);
        rowAction(document.querySelector('#managementRows tr'),'Khóa');assert(document.getElementById('managementConfirm').open,'Confirmation did not open');submit('managementConfirmForm');
        await waitFor(()=>document.getElementById('managementActive').textContent==='0');
        assert(calls.filter(call=>call.method==='PATCH').length===1,'Status submitted more than once');
        assert(document.getElementById('managementRows').textContent.includes('Đã khóa'),'Updated customer not rendered');
        `},
    {page:'employees',role:'HR_MANAGER',name:'employee-create-role-safe-contract',setup:`
        let employees=[{id:'NV02',fullName:'Nhân viên cũ',email:'staff@example.com',phone:'0987654321',role:'ORDER_STAFF',status:'WORKING',hireDate:'2026-10-01'}];
        const fixture=call=>{if(call.method==='GET')return employees;if(call.method==='POST'){assert(call.body.password===' Created#123 ','Password whitespace changed');assert(call.body.hireDate==='2026-10-12','Hire date changed');const created={...call.body,id:'NV03',status:'WORKING'};delete created.password;employees.unshift(created);return created;}const target=employees.find(e=>call.path.includes(e.id));target.role=call.body.role;return target;};
        `,run:`
        await waitFor(()=>document.getElementById('managementTotal').textContent==='1');
        assert(!document.querySelector('#employeeRole option[value=ADMIN]'),'HR can choose ADMIN');
        document.getElementById('employeeCreate').click();
        fields({employeeFullName:'Nhân viên mới',employeeEmail:'new@example.com',employeePhone:'0987654322',employeePassword:' Created#123 ',employeeRole:'MENU_MANAGER',employeeHireDate:'2026-10-12',employeeAddress:'123 Nguyễn Huệ'});
        submit('employeeCreateForm');await waitFor(()=>document.getElementById('managementTotal').textContent==='2');
        assert(document.getElementById('employeePassword').value==='','Saved password remains in form');
        const oldRow=[...document.querySelectorAll('#managementRows tr')].find(row=>row.textContent.includes('NV02'));
        rowAction(oldRow,'Đổi chức vụ');
        fields({employeeNewRole:'HR_MANAGER'});submit('employeeRoleForm');
        await waitFor(()=>oldRow!==[...document.querySelectorAll('#managementRows tr')].find(row=>row.textContent.includes('NV02')));
        assert(document.getElementById('managementRows').textContent.includes('Quản lý nhân sự'),'Role change not rendered');
        `},
    {page:'promotions',role:'PROMOTION_MANAGER',name:'promotion-create-edit-toggle-safe-contract',setup:`
        let promotions=[{promotionId:'KM01',code:'SAFE',name:'Ưu đãi <svg onload=alert(1)>',discountType:'PERCENT',discountValue:10,minimumOrderValue:0,maximumDiscount:null,startAt:'2020-01-01T00:00:00',endAt:'2099-01-01T00:00:00',status:'ACTIVE'}];
        const fixture=call=>{if(call.method==='GET')return promotions;if(call.method==='POST'){assert(call.body.maximumDiscount===null,'Empty cap did not become null');assert(call.body.discountValue===10,'Amount did not become number');assert(call.body.startAt==='2026-10-12T08:00:00','Timestamp contract invalid');const created={...call.body,promotionId:'KM02'};promotions.unshift(created);return created;}let item=promotions.find(p=>call.path.includes(p.promotionId));item=Object.assign(item,call.body);return item;};
        `,run:`
        await waitFor(()=>document.querySelectorAll('.management-promo-card').length===1);
        assert(!document.querySelector('#managementPromotionList svg[onload]'),'Promotion name inserted as HTML');
        document.getElementById('promotionCreate').click();fields({promotionCode:'NEW10',promotionName:'Khuyến mãi mới',promotionType:'PERCENT',promotionValue:'10',promotionMinimum:'0',promotionMaximum:'',promotionStart:'2026-10-12T08:00',promotionEnd:'2026-10-13T20:00'});
        submit('promotionForm');await waitFor(()=>document.querySelectorAll('.management-promo-card').length===2);
        const created=document.querySelector('.management-promo-card');rowAction(created,'Chỉnh sửa');
        fields({promotionType:'FIXED_AMOUNT',promotionValue:'5000'});document.getElementById('promotionType').dispatchEvent(new Event('change'));submit('promotionForm');
        await waitFor(()=>calls.some(call=>call.method==='PATCH'&&call.body.discountType==='FIXED_AMOUNT'));
        await waitFor(()=>!document.getElementById('promotionDialog').open);
        rowAction(document.querySelector('.management-promo-card'),'Tắt áp dụng');submit('managementConfirmForm');
        await waitFor(()=>document.querySelector('.management-promo-card').textContent.includes('Đã tắt'));
        assert(calls.some(call=>call.path.endsWith('/KM02/status')&&call.body.status==='INACTIVE'),'Status endpoint mismatch');
        `},
    {page:'customers',role:'ADMIN',name:'customer-pagination-filter-keyboard-focus',setup:`
        const customers=Array.from({length:24},(_,i)=>({id:'KH'+String(i+1).padStart(2,'0'),fullName:'Khách '+(i+1),email:'customer'+i+'@example.com',phone:'0987654321',status:i%5===0?'LOCKED':'ACTIVE',registeredAt:'2026-10-01T10:00:00',orderCount:1,totalSpent:15000}));
        const fixture=()=>customers;
        `,run:`
        await waitFor(()=>document.getElementById('managementTotal').textContent==='24');
        assert(document.querySelectorAll('#managementRows tr').length===10,'First page not limited to 10');
        const next=document.querySelector('.pagination-controls button:last-child');next.click();
        assert(document.querySelector('#managementRows tr').textContent.includes('KH11'),'Second page started at wrong row');
        next.click();assert(document.querySelectorAll('#managementRows tr').length===4&&next.disabled,'Last page boundary failed');
        document.getElementById('managementStatus').value='LOCKED';document.getElementById('managementStatus').dispatchEvent(new Event('change'));
        assert(document.querySelectorAll('#managementRows tr').length===5&&document.querySelector('.pagination-controls span').textContent==='Trang 1/1','Filter did not reset pagination');
        const menu=openActions(document.querySelector('#managementRows tr'));
        assert(document.activeElement===menu.querySelector('button'),'First action did not receive focus');
        document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
        assert(!document.querySelector('.admin-dropdown-menu:not([hidden])')&&document.activeElement.classList.contains('admin-actions-trigger'),'Escape did not restore focus');
        const select=document.getElementById('managementStatus');select.focus();
        assert(getComputedStyle(select).outlineStyle==='none'&&getComputedStyle(select).appearance==='none','Select uses native double focus/arrow');
        const search=document.getElementById('managementSearch');search.value='khach 21';search.dispatchEvent(new Event('input'));
        await waitFor(()=>document.querySelectorAll('#managementRows tr').length===1);
        assert(document.querySelector('#managementRows tr').textContent.includes('KH21'),'Debounced Vietnamese search failed');
        `}
];
let current;
const server = http.createServer((request,response) => {
    const asset = /^\/crave\/assets\/(js|css)\/([\w.-]+)$/.exec(request.url);
    if (asset) {
        response.setHeader('Content-Type',asset[1]==='js'?'text/javascript; charset=utf-8':'text/css; charset=utf-8');
        response.end(fs.readFileSync(path.join(project,'frontend/assets',asset[1],asset[2])));return;
    }
    response.setHeader('Content-Type','text/html; charset=utf-8');
    const markup = fs.readFileSync(path.join(project,'frontend/WEB-INF/views/admin',current.page+'.jsp'),'utf8')
        .replace(/<%@[^]*?%>/g,'').replace(/<c:set[^]*?\/>/g,'')
        .replace(/<c:url value='([^']*)'\s*\/>/g,(_,value)=>'/crave'+value);
    response.end(`<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/crave/assets/css/admin.css"></head>
        <body data-page="${current.page}" data-context-path="/crave" data-role="${current.role}"><script>${common}${current.setup}</script><script src="/crave/assets/js/admin.js"></script>${markup}
        <script>document.addEventListener('DOMContentLoaded',async()=>{const result=document.createElement('pre');result.id='smoke-result';try{${current.run}result.textContent='PASS ${current.name}';}catch(error){result.textContent='FAIL '+error.message;}document.body.append(result);});</script></body></html>`);
});
async function browser(url,index) {
    return new Promise((resolve,reject) => {
        let output='';
        const child=spawn(chrome,['--headless=new','--no-sandbox','--disable-gpu','--disable-extensions','--no-first-run',
            '--no-default-browser-check','--user-data-dir='+path.join(work,'profile-'+index),'--dump-dom','--virtual-time-budget=5000',url],{windowsHide:true});
        child.stdout.on('data',data=>output+=data);child.stderr.resume();
        const timeout=setTimeout(()=>{child.kill();reject(new Error('Browser timed out: '+current.name));},20000);
        child.on('error',reject);child.on('close',()=>{clearTimeout(timeout);resolve(output);});
    });
}
(async()=>{
    await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
    let failed=0;
    for(let index=0;index<cases.length;index++) {
        current=cases[index];
        const html=await browser('http://127.0.0.1:'+server.address().port+'/crave/admin/'+current.page,index);
        fs.writeFileSync(path.join(work,current.name+'.html'),html);
        const result=[...html.matchAll(/<pre id="smoke-result">([^<]*)<\/pre>/g)].at(-1)?.[1]||'FAIL no browser result';
        console.log(current.name+': '+result);if(!result.startsWith('PASS '))failed++;
    }
    server.close();console.log(`Admin browser forms: ${cases.length-failed}/${cases.length} passed. Artifacts: ${work}`);process.exitCode=failed?1:0;
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
