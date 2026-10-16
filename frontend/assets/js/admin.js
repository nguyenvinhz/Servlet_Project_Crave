(() => {
    'use strict';
    document.addEventListener('DOMContentLoaded', () => {
        const toggle = document.getElementById('sidebarToggle');
        try { if (localStorage.getItem('crave.admin.collapsed') === 'true') document.body.classList.add('collapsed'); } catch { }
        toggle?.setAttribute('aria-expanded', String(!document.body.classList.contains('collapsed')));
        toggle?.addEventListener('click', () => {
            const collapsed = document.body.classList.toggle('collapsed');
            toggle.setAttribute('aria-expanded', String(!collapsed));
            try { localStorage.setItem('crave.admin.collapsed', String(collapsed)); } catch { }
        });
        const mobileToggle = document.getElementById('mobileSidebarToggle');
        mobileToggle?.addEventListener('click', () => {
            const open = document.body.classList.toggle('sidebar-open');
            mobileToggle.setAttribute('aria-expanded', String(open));
        });
        document.addEventListener('click', event => {
            if (document.body.classList.contains('sidebar-open') && !event.target.closest('.admin-sidebar,#mobileSidebarToggle')) {
                document.body.classList.remove('sidebar-open');
                mobileToggle?.setAttribute('aria-expanded', 'false');
            }
        });
        document.querySelectorAll('form[data-confirm]').forEach(form => {
            form.addEventListener('submit', event => {
                if (!window.confirm(form.dataset.confirm)) event.preventDefault();
            });
        });
    });
})();
