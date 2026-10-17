"use strict";

document.addEventListener('DOMContentLoaded', () => {
    const contextPath = document.body.dataset.contextPath || '';
    const editor = document.getElementById('cartItemEditor');
    const form = document.getElementById('cartItemEditorForm');
    const modal = document.getElementById('promosModal');
    let returnFocus;

    function submitCart(action, values = {}) {
        const actionForm = document.createElement('form');
        actionForm.method = 'post';
        actionForm.action = contextPath + '/cart/action';
        const fields = { action, returnTo: '/cart', ...values };
        Object.entries(fields).forEach(([name, value]) => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = name;
            input.value = value;
            actionForm.append(input);
        });
        document.body.append(actionForm);
        actionForm.submit();
    }

    document.querySelectorAll('.btn-edit').forEach(button => button.addEventListener('click', () => {
        window.location.href = contextPath + '/cart?' + new URLSearchParams({ edit: button.dataset.id });
    }));
    document.querySelectorAll('.cart-item-card').forEach(card => card.addEventListener('click', event => {
        if (!event.target.closest('button, a, form')) {
            window.location.href = contextPath + '/cart?' + new URLSearchParams({ edit: card.dataset.id });
        }
    }));
    document.querySelectorAll('.btn-dec, .btn-inc').forEach(button => button.addEventListener('click', () => {
        const quantity = Number(button.dataset.qty);
        if (quantity === 0 && !confirm('Bạn có chắc chắn muốn xóa món này khỏi giỏ hàng?')) return;
        submitCart(quantity === 0 ? 'remove' : 'update', { cartItemId: button.dataset.id, quantity });
    }));
    document.querySelectorAll('.btn-remove').forEach(button => button.addEventListener('click', () => {
        submitCart('remove', { cartItemId: button.dataset.id });
    }));
    document.getElementById('clearCartBtn')?.addEventListener('click', () => {
        if (confirm('Bạn có chắc chắn muốn xóa toàn bộ món trong giỏ hàng?')) submitCart('clear');
    });
    document.getElementById('retryFetchCartBtn')?.addEventListener('click', () => window.location.reload());
    document.getElementById('proceedCheckoutBtn')?.addEventListener('click', () => {
        window.location.href = contextPath + '/checkout';
    });

    function closePromotions() {
        modal?.classList.remove('active');
        modal?.setAttribute('aria-hidden', 'true');
        returnFocus?.focus();
    }
    document.getElementById('viewPromosBtn')?.addEventListener('click', event => {
        event.preventDefault();
        returnFocus = document.activeElement;
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');
        document.getElementById('promosModalClose').focus();
    });
    document.getElementById('promosModalClose')?.addEventListener('click', closePromotions);
    modal?.addEventListener('click', event => { if (event.target === modal) closePromotions(); });
    modal?.addEventListener('keydown', event => {
        if (event.key === 'Escape') closePromotions();
        if (event.key !== 'Tab') return;
        const controls = [...modal.querySelectorAll('button:not(:disabled), a[href], input:not([type="hidden"]):not(:disabled)')];
        const first = controls[0], last = controls.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });

    function closeEditor() {
        if (editor?.open) editor.close();
        const address = new URL(window.location.href);
        address.searchParams.delete('edit');
        window.history.replaceState(null, '', address);
        document.querySelector('.btn-edit')?.focus();
    }
    document.getElementById('cartEditorClose')?.addEventListener('click', closeEditor);
    document.getElementById('cartEditorCancel')?.addEventListener('click', closeEditor);
    editor?.addEventListener('cancel', event => { event.preventDefault(); closeEditor(); });
    editor?.addEventListener('click', event => {
        const rect = editor.getBoundingClientRect();
        if (event.target === editor && (event.clientX < rect.left || event.clientX > rect.right
                || event.clientY < rect.top || event.clientY > rect.bottom)) closeEditor();
    });
    function updateEditorTotal() {
        if (!form) return;
        let price = Number(form.dataset.basePrice) || 0;
        document.querySelectorAll('#cartEditorOptions input:checked').forEach(option => price += Number(option.dataset.extra) || 0);
        const quantity = Number(document.getElementById('cartEditorQuantity')?.value) || 0;
        document.getElementById('cartEditorTotal').textContent = (price * quantity).toLocaleString('vi-VN') + ' đ';
    }
    form?.addEventListener('input', updateEditorTotal);
    form?.addEventListener('submit', event => {
        if (!form.reportValidity()) { event.preventDefault(); return; }
        form.querySelectorAll('input[data-submitted-option]').forEach(input => input.remove());
        form.querySelectorAll('#cartEditorOptions input:checked').forEach(option => {
            if (!option.value) return;
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'optionIds';
            input.value = option.value;
            input.dataset.submittedOption = '';
            form.append(input);
        });
        document.getElementById('cartEditorSave').disabled = true;
    });
    if (editor?.dataset.open === 'true') {
        editor.showModal();
        updateEditorTotal();
    }
});