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
    let activeDropdown = null, dropdownId = 0;
    function closeDropdown(restoreFocus = false) {
        if (!activeDropdown) return;
        const {trigger, menu, parent, next} = activeDropdown;
        activeDropdown = null;
        menu.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
        parent.insertBefore(menu, next?.parentNode === parent ? next : null);
        if (restoreFocus && trigger.isConnected) trigger.focus();
    }
    function placeDropdown() {
        if (!activeDropdown) return;
        const {trigger, menu} = activeDropdown;
        if (!trigger.isConnected) {closeDropdown();return;}
        const rect = trigger.getBoundingClientRect();
        const width = menu.offsetWidth, height = menu.offsetHeight, gutter = 12;
        const left = Math.max(gutter, Math.min(rect.right - width, innerWidth - width - gutter));
        const top = rect.bottom + height + 8 <= innerHeight - gutter ? rect.bottom + 8 : Math.max(gutter, rect.top - height - 8);
        menu.style.left = `${left}px`;
        menu.style.top = `${top}px`;
    }
    function dropdown(trigger, menu) {
        menu.classList.add('admin-dropdown-menu');
        menu.hidden = true;
        menu.id ||= 'admin-menu-' + (++dropdownId);
        menu.setAttribute('role', 'menu');
        trigger.setAttribute('aria-haspopup', 'menu');
        trigger.setAttribute('aria-expanded', 'false');
        trigger.setAttribute('aria-controls', menu.id);
        menu.querySelectorAll('button,a').forEach(item => item.setAttribute('role', 'menuitem'));
        function open(last = false) {
            closeDropdown();
            const parent = menu.parentNode, next = menu.nextSibling;
            activeDropdown = {trigger, menu, parent, next};
            // A viewport-level layer keeps actions visible above scrolling tables.
            (trigger.closest('dialog[open]') || document.body).appendChild(menu);
            menu.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            placeDropdown();
            const enabled = [...menu.querySelectorAll('button:not(:disabled),a[href]')];
            (last ? enabled.at(-1) : enabled[0])?.focus();
        }
        trigger.addEventListener('click', () => activeDropdown?.trigger === trigger ? closeDropdown(true) : open());
        trigger.addEventListener('keydown', event => {
            if (['ArrowDown', 'ArrowUp'].includes(event.key)) {event.preventDefault();open(event.key === 'ArrowUp');}
        });
        menu.addEventListener('click', event => {if (event.target.closest('button:not(:disabled),a[href]')) closeDropdown();});
        return trigger;
    }
    function enhanceActions(scope = document) {
        scope.querySelectorAll('.row-actions,.management-row-actions,.management-promo-actions').forEach(host => {
            if (host.dataset.dropdownReady || !host.querySelector('button,a')) return;
            host.dataset.dropdownReady = 'true';
            const trigger = document.createElement('button');
            trigger.type = 'button';
            trigger.className = 'admin-actions-trigger';
            const subject = host.closest('tr,article')?.querySelector('strong,h2')?.textContent.trim();
            trigger.setAttribute('aria-label', 'Thao tác' + (subject ? ' cho ' + subject : ''));
            trigger.innerHTML = '<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="5" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="19" cy="12" r="1.5"/></svg>';
            const menu = document.createElement('div');
            while (host.firstChild) menu.appendChild(host.firstChild);
            host.append(trigger, menu);
            dropdown(trigger, menu);
        });
    }
    function pagination(host, {pageSize = 10, label = 'dòng', onChange = () => {}} = {}) {
        let current = 1, total = 0;
        const nav = document.createElement('nav');
        nav.className = 'admin-pagination';nav.setAttribute('aria-label', 'Phân trang ' + label);
        const info = document.createElement('span');info.className = 'pagination-info';info.setAttribute('aria-live','polite');
        const controls = document.createElement('div');controls.className = 'pagination-controls';
        const prev = document.createElement('button'), next = document.createElement('button'), pages = document.createElement('span');
        [prev,next].forEach(button => {button.type = 'button';button.className = 'button outline';});
        prev.textContent = 'Trước';next.textContent = 'Sau';
        controls.append(prev,pages,next);nav.append(info,controls);host.appendChild(nav);
        function refresh() {
            const count = Math.max(1, Math.ceil(total / pageSize));current = Math.max(1,Math.min(current,count));
            info.textContent = total ? `${(current-1)*pageSize+1}–${Math.min(current*pageSize,total)} / ${total} ${label}` : `0 ${label}`;
            pages.textContent = `Trang ${current}/${count}`;
            prev.disabled = current === 1;next.disabled = current === count;
        }
        prev.addEventListener('click', () => {closeDropdown();current--;onChange();});
        next.addEventListener('click', () => {closeDropdown();current++;onChange();});
        refresh();
        return {
            reset() {current = 1;},
            slice(values) {total = values.length;refresh();return values.slice((current-1)*pageSize,current*pageSize);}
        };
    }
    function debounce(action, delay = 250) {let timeout;return (...args) => {clearTimeout(timeout);timeout = setTimeout(() => action(...args),delay);};}
    window.CraveAdmin = {ctx,request,escape,date,badge,toast,labels,dropdown,enhanceActions,pagination,closeDropdown,debounce,money:value => Number(value || 0).toLocaleString('vi-VN') + '\u00a0₫'};
    document.addEventListener('DOMContentLoaded',() => {
        const mobileToggle = document.getElementById('mobileSidebarToggle');
        mobileToggle?.addEventListener('click',() => {const open = document.body.classList.toggle('sidebar-open'); mobileToggle.setAttribute('aria-expanded',String(open));});
        document.addEventListener('click',event => {if(document.body.classList.contains('sidebar-open') && !event.target.closest('.admin-sidebar,#mobileSidebarToggle')) {document.body.classList.remove('sidebar-open');mobileToggle?.setAttribute('aria-expanded','false');}});
        document.getElementById('adminLogout')?.addEventListener('click',async () => {try {await request('/api/auth/logout',{method:'POST'});window.location.href=ctx+'/auth/login';} catch(error){toast(error.message,'error');}});
        const profile = document.getElementById('adminProfileToggle'), profileMenu = document.getElementById('adminProfileMenu');
        if (profile && profileMenu) dropdown(profile,profileMenu);
        enhanceActions();
        document.addEventListener('click',event => {if(activeDropdown && !activeDropdown.menu.contains(event.target) && !activeDropdown.trigger.contains(event.target))closeDropdown();});
        document.addEventListener('focusin',event => {if(activeDropdown && !activeDropdown.menu.contains(event.target) && !activeDropdown.trigger.contains(event.target))closeDropdown();});
        window.addEventListener('resize',placeDropdown);
        document.addEventListener('scroll',placeDropdown,true);
        document.addEventListener('keydown',event => {
            if (activeDropdown) {
                if (event.key === 'Escape') {event.preventDefault();closeDropdown(true);return;}
                if (['ArrowDown','ArrowUp','Home','End'].includes(event.key) && activeDropdown.menu.contains(event.target)) {
                    event.preventDefault();const nodes = [...activeDropdown.menu.querySelectorAll('button:not(:disabled),a[href]')];
                    const index = nodes.indexOf(document.activeElement);
                    nodes[event.key === 'Home' ? 0 : event.key === 'End' ? nodes.length-1 : (index+(event.key==='ArrowDown'?1:-1)+nodes.length)%nodes.length]?.focus();
                }
            }
            if(event.key==='Escape')document.querySelectorAll('.modal-backdrop').forEach(modal=>{modal.style.display='none';modal.hidden=true;});
        });
    });
})();
