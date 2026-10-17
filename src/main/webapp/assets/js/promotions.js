"use strict";

document.addEventListener('DOMContentLoaded', () => {
    const feedback = document.getElementById('promosFeedback');
    document.getElementById('promosRetry')?.addEventListener('click', () => window.location.reload());
    document.querySelectorAll('.btn-copy-code').forEach(button => {
        button.addEventListener('click', async () => {
            const code = button.dataset.code || '';
            try {
                await navigator.clipboard.writeText(code);
                feedback.textContent = 'Đã sao chép mã ' + code + '.';
                button.textContent = 'Đã sao chép ✓';
            } catch (_) {
                feedback.textContent = 'Mã ưu đãi: ' + code + '. Bạn có thể sao chép và nhập mã trong giỏ hàng.';
            }
        });
    });
});