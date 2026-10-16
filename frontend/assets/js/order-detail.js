'use strict';
(() => {
    const page = document.querySelector('[data-payment-pending="true"]');
    if (!page) return;
    let editing = false;
    page.querySelectorAll('form').forEach(form => {
        form.addEventListener('input', () => { editing = true; });
        form.addEventListener('submit', () => { editing = true; });
    });
    function refresh() {
        if (!editing && document.visibilityState === 'visible' && !document.activeElement?.closest('form')) {
            window.location.replace(window.location.pathname);
            return;
        }
        window.setTimeout(refresh, 5000);
    }
    window.setTimeout(refresh, 5000);
})();
