document.addEventListener('DOMContentLoaded', () => {
    const stepper = document.querySelector('[data-order-status]');
    if (stepper) {
        const steps = Array.from(stepper.querySelectorAll('[data-step]'));
        const active = steps.findIndex(step => step.dataset.step === stepper.dataset.orderStatus);
        steps.forEach((step, index) => {
            step.classList.toggle('done', index < active);
            step.classList.toggle('active', index === active);
            step.querySelector('.step-icon').textContent = index < active ? '✓' : String(index + 1);
        });
    }
    const context = document.body.dataset.contextPath || '';
    let busy = false;
    async function refresh() {
        const card = document.getElementById('payosPayment');
        if (!card || card.dataset.pending !== 'true' || document.hidden || busy) return;
        busy = true;
        try {
            const response = await fetch(context + '/payos/status?orderId=' + encodeURIComponent(card.dataset.orderId),
                {credentials:'same-origin', headers:{Accept:'text/html'}});
            if (response.redirected || !response.ok) return;
            const document = new DOMParser().parseFromString(await response.text(), 'text/html');
            const updated = document.getElementById('payosPayment');
            if (!updated) return;
            if (updated.dataset.paid === 'true') { location.reload(); return; }
            card.replaceWith(updated);
        } catch (_) { /* Webhook vẫn xác nhận thanh toán; trang thử lại ở lần kiểm tra kế tiếp. */ }
        finally { busy = false; }
    }
    const timer = setInterval(refresh, 5000);
    window.addEventListener('pagehide', () => clearInterval(timer), {once:true});
    document.addEventListener('visibilitychange', refresh);
});