'use strict';
document.addEventListener('DOMContentLoaded',()=>{
    const page=document.body.dataset.page;
    if(!['customers','employees','promotions'].includes(page))return;
    const A=window.CraveAdmin,byId=id=>document.getElementById(id),employee=page==='employees',promotion=page==='promotions';
    const items=[...document.querySelectorAll(promotion?'[data-promotion]':'[data-account]')];
    const roleLabels={ADMIN:'Quản trị viên',MENU_MANAGER:'Quản lý thực đơn',ORDER_STAFF:'Xử lý đơn hàng',PROMOTION_MANAGER:'Quản lý khuyến mãi',HR_MANAGER:'Quản lý nhân sự'};
    const field=(form,name,value)=>{form.elements.namedItem(name).value=value??'';};
    const normalized=value=>String(value||'').normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').toLowerCase();
    const dateValue=value=>new Date(String(value)+(String(value).includes('T')?'+07:00':'T00:00:00+07:00'));
    function datetimeInput(date){const p=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(date).map(part=>[part.type,part.value]));return p.year+'-'+p.month+'-'+p.day+'T'+p.hour+':'+p.minute;}
    document.querySelectorAll('[data-date]').forEach(node=>node.textContent=A.date(node.dataset.date,node.hasAttribute('data-time')));
    items.forEach(node=>{
        const item=node.dataset;
        if(promotion){
            const start=dateValue(item.startAt).getTime(),end=dateValue(item.endAt).getTime(),now=Date.now();
            item.state=item.status!=='ACTIVE'?'INACTIVE':now<start?'UPCOMING':now>end?'EXPIRED':'CURRENT';
            const badge=node.querySelector('[data-promo-state]');badge.textContent={CURRENT:'Đang áp dụng',UPCOMING:'Sắp diễn ra',EXPIRED:'Hết hạn',INACTIVE:'Đã tắt'}[item.state];badge.dataset.tone=item.state==='CURRENT'?'active':item.state==='UPCOMING'?'upcoming':'inactive';
            node.querySelector('.management-promo-progress span').style.width=Math.max(0,Math.min(100,(now-start)/(end-start)*100))+'%';
        }else{
            node.querySelector('.management-avatar').textContent=(item.fullName||'Chưa có tên').trim().split(/\s+/).slice(-2).map(word=>word[0]).join('').toUpperCase();
            const role=node.querySelector('[data-role-label]');if(role)role.textContent=roleLabels[item.role]||'Chưa phân công';
        }
    });
    if(!promotion){const active=items.filter(node=>node.dataset.status===(employee?'WORKING':'ACTIVE')).length;byId('managementTotal').textContent=items.length;byId('managementActive').textContent=active;byId('managementInactive').textContent=items.length-active;}
    let empty=document.querySelector('.management-no-results');
    if(!empty){empty=document.createElement(promotion?'div':'tr');empty.className=promotion?'admin-card management-empty management-no-results':'management-no-results';if(promotion)empty.textContent='Không tìm thấy chương trình khuyến mãi phù hợp.';else{const td=document.createElement('td');td.colSpan=employee?7:8;td.className='management-empty';td.textContent='Không tìm thấy '+(employee?'nhân viên':'khách hàng')+' phù hợp.';empty.appendChild(td);}byId(promotion?'managementPromotionList':'managementRows').appendChild(empty);}
    function filter(){const query=normalized(byId('managementSearch').value),status=byId('managementStatus').value,role=byId('managementRole')?.value;let count=0;items.forEach(node=>{const d=node.dataset;const visible=normalized([d.fullName,d.id,d.email,d.phone,d.name,d.code].join(' ')).includes(query)&&(!status||(promotion?d.state:d.status)===status)&&(!role||d.role===role);node.hidden=!visible;if(visible)count++;});empty.hidden=count>0;byId('managementCount').textContent=count+' / '+items.length+' '+(promotion?'chương trình':employee?'nhân viên':'khách hàng');}
    ['managementSearch','managementStatus','managementRole'].forEach(id=>byId(id)?.addEventListener(id==='managementSearch'?'input':'change',filter));filter();
    byId('managementRefresh').addEventListener('click',()=>location.reload());
    function closeMenus(){document.querySelectorAll('.management-action-menu.is-open').forEach(menu=>{menu.classList.remove('is-open','open-upward');menu.querySelector('button').setAttribute('aria-expanded','false');});}
    document.querySelectorAll('.management-dots-btn').forEach(button=>button.addEventListener('click',event=>{event.stopPropagation();const menu=button.closest('.management-action-menu'),open=menu.classList.contains('is-open');closeMenus();if(!open){menu.classList.add('is-open');button.setAttribute('aria-expanded','true');if(innerHeight-button.getBoundingClientRect().bottom<130)menu.classList.add('open-upward');}}));
    document.addEventListener('click',event=>{if(!event.target.closest('.management-action-menu'))closeMenus();});
    document.addEventListener('keydown',event=>{if(event.key==='Escape')closeMenus();});
    document.querySelectorAll('[data-status-change]').forEach(button=>button.addEventListener('click',()=>{
        closeMenus();const item=button.closest('[data-account],[data-promotion]').dataset;
        const enabled=item.status===(employee?'WORKING':'ACTIVE'),name=promotion?item.name:item.fullName;
        const action=promotion?(enabled?'Tắt khuyến mãi':'Bật khuyến mãi'):employee?(enabled?'Cho nhân viên nghỉ việc':'Cho nhân viên làm việc lại'):(enabled?'Khóa tài khoản':'Mở khóa tài khoản');
        byId('managementConfirmTitle').textContent=action;byId('managementConfirmSubmit').textContent=action;
        byId('managementConfirmMessage').textContent=action+' “'+name+'”? '+(promotion?(enabled?'Khách hàng sẽ không thể áp dụng chương trình này cho đơn mới.':'Khách hàng có thể áp dụng chương trình khi đủ điều kiện và trong thời gian hiệu lực.'):(enabled?'Tài khoản sẽ không thể đăng nhập cho đến khi được kích hoạt lại.':'Tài khoản có thể đăng nhập trở lại.'));
        const form=byId('managementConfirmForm');field(form,'id',item.id);field(form,'status',promotion?(enabled?'INACTIVE':'ACTIVE'):employee?(enabled?'ON_LEAVE':'WORKING'):(enabled?'LOCKED':'ACTIVE'));byId('managementConfirm').showModal();
    }));
    if(employee){
        if(document.body.dataset.role!=='ADMIN')document.querySelectorAll('[data-admin-only]').forEach(option=>option.remove());
        byId('employeeCreate').addEventListener('click',()=>{byId('employeeCreateForm').reset();byId('employeeHireDate').value=datetimeInput(new Date()).slice(0,10);byId('employeeCreateDialog').showModal();});
        document.querySelectorAll('[data-role-change]').forEach(button=>button.addEventListener('click',()=>{closeMenus();const item=button.closest('[data-account]').dataset;const form=byId('employeeRoleForm');field(form,'id',item.id);field(form,'role',item.role);byId('employeeRoleName').textContent=item.fullName+' · '+item.id;byId('employeeRoleDialog').showModal();}));
    }
    if(promotion){
        function typeChanged(){const percent=byId('promotionType').value==='PERCENT';byId('promotionValueLabel').textContent=percent?'Giảm giá (%)':'Số tiền giảm (đ)';byId('promotionValue').max=percent?'100':'999999999999';}
        function openPromotion(item){const form=byId('promotionForm');form.reset();field(form,'action',item?'update':'create');field(form,'id',item?.id);byId('promotionDialogTitle').textContent=item?'Chỉnh sửa khuyến mãi':'Thêm khuyến mãi';if(item){['code','name','discountType','discountValue','minimumOrderValue','maximumDiscount','status'].forEach(name=>field(form,name,item[name]));byId('promotionStart').value=item.startAt.slice(0,16);byId('promotionEnd').value=item.endAt.slice(0,16);}else{byId('promotionStart').value=datetimeInput(new Date());byId('promotionEnd').value=datetimeInput(new Date(Date.now()+7*86400000));}typeChanged();byId('promotionFormError').hidden=true;byId('promotionDialog').showModal();}
        byId('promotionCreate').addEventListener('click',()=>openPromotion());document.querySelectorAll('[data-promotion-edit]').forEach(button=>button.addEventListener('click',()=>openPromotion(button.closest('[data-promotion]').dataset)));
        byId('promotionType').addEventListener('change',typeChanged);
        byId('promotionForm').addEventListener('submit',event=>{if(byId('promotionEnd').value<=byId('promotionStart').value){event.preventDefault();byId('promotionFormError').textContent='Thời gian kết thúc phải sau thời gian bắt đầu.';byId('promotionFormError').hidden=false;}});
    }
    document.querySelectorAll('[data-close-dialog]').forEach(button=>button.addEventListener('click',()=>button.closest('dialog').close()));
});
