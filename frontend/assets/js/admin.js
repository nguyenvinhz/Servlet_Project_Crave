(() => {
    'use strict';
    const ctx = document.body.dataset.contextPath || '';
    window.CONTEXT_PATH = ctx;
    const labels = {PENDING_CONFIRMATION:'Chờ xác nhận',PREPARING:'Đang chuẩn bị',DELIVERING:'Đang giao',COMPLETED:'Hoàn tất',CANCELLED:'Đã hủy',PENDING:'Chờ thanh toán',SUCCESS:'Đã thanh toán',FAILED:'Thất bại',REFUNDED:'Đã hoàn tiền',ACTIVE:'Hoạt động',LOCKED:'Đã khóa',WORKING:'Đang làm việc',ON_LEAVE:'Đã nghỉ việc',INACTIVE:'Tạm ngưng',AVAILABLE:'Đang bán',UNAVAILABLE:'Tạm hết',ADMIN:'Quản trị viên',HR_MANAGER:'Quản lý nhân sự',MENU_MANAGER:'Quản lý thực đơn',ORDER_STAFF:'Nhân viên đơn hàng',PROMOTION_MANAGER:'Quản lý khuyến mãi'};
    const escape = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
    function toast(message, type = 'success') {
        const host = document.getElementById('adminToastContainer') || document.getElementById('toastContainer');
        if (!host) return;
        const el = document.createElement('div'); el.className = `toast show toast-${type}`; el.textContent = message;
        host.appendChild(el); setTimeout(() => el.remove(), 4500);
    }
    async function request(path, options = {}) {
        let response;
        try { response = await fetch(ctx + path, {...options,credentials:'same-origin',headers:{Accept:'application/json',...(options.body ? {'Content-Type':'application/json'} : {}),...options.headers}}); }
        catch { throw new Error('Không thể kết nối máy chủ. Vui lòng thử lại.'); }
        if (response.status === 401) { window.location.href = ctx + '/auth/login?reason=session-expired'; throw new Error('Phiên đăng nhập đã hết hạn.'); }
        let payload;
        try { payload = await response.json(); } catch { throw new Error('Máy chủ trả về dữ liệu không hợp lệ.'); }
        if (!response.ok || !payload.success) {
            const error = new Error(payload.error?.message || payload.message || 'Không thể thực hiện thao tác.');
            error.details = payload.error?.fieldErrors || payload.error?.details;
            throw error;
        }
        return payload.data;
    }
    function date(value, time = true) {
        if (!value) return '—';
        const source = String(value), zoned = /Z$|[+-]\d{2}:\d{2}$/.test(source) ? source : source.includes('T') ? source+'+07:00' : source+'T00:00:00+07:00';
        const d = new Date(zoned); if (Number.isNaN(d.getTime())) return '—';
        return d.toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',year:'numeric',...(time ? {hour:'2-digit',minute:'2-digit'} : {})});
    }
    function badge(status) {
        const tone = ['SUCCESS','COMPLETED','ACTIVE','WORKING','AVAILABLE'].includes(status) ? 'green' : ['FAILED','CANCELLED','LOCKED','UNAVAILABLE'].includes(status) ? 'red' : ['DELIVERING','REFUNDED'].includes(status) ? 'blue' : 'orange';
        return `<span class="badge ${tone}">${escape(labels[status] || status || '—')}</span>`;
    }
    window.CraveAdmin = {ctx,request,escape,date,badge,toast,labels,money:value => Number(value || 0).toLocaleString('vi-VN') + ' ₫'};
    document.addEventListener('DOMContentLoaded',() => {
        const toggle = document.getElementById('sidebarToggle');
        try { if (localStorage.getItem('crave.admin.collapsed') === 'true') document.body.classList.add('collapsed'); } catch { /* Sidebar remains usable when storage is blocked. */ }
        toggle?.setAttribute('aria-expanded',String(!document.body.classList.contains('collapsed')));
        toggle?.addEventListener('click', () => { const collapsed = document.body.classList.toggle('collapsed'); toggle.setAttribute('aria-expanded',String(!collapsed)); try {localStorage.setItem('crave.admin.collapsed',String(collapsed));} catch { /* Keep the current session preference. */ } });
        const mobileToggle = document.getElementById('mobileSidebarToggle');
        mobileToggle?.addEventListener('click',() => {const open = document.body.classList.toggle('sidebar-open'); mobileToggle.setAttribute('aria-expanded',String(open));});
        document.addEventListener('click',event => {if(document.body.classList.contains('sidebar-open') && !event.target.closest('.admin-sidebar,#mobileSidebarToggle')) {document.body.classList.remove('sidebar-open');mobileToggle?.setAttribute('aria-expanded','false');}});
        document.getElementById('adminLogout')?.addEventListener('click',async () => {try {await request('/api/auth/logout',{method:'POST'});window.location.href=ctx+'/auth/login';} catch(error){toast(error.message,'error');}});
        document.addEventListener('keydown',event => {if(event.key==='Escape')document.querySelectorAll('.modal-backdrop').forEach(modal=>{modal.style.display='none';modal.hidden=true;});});
    });
})();
