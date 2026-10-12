
function changeQuantity(delta) {
    const qtyInput = document.getElementById('orderQuantity');
    let current = parseInt(qtyInput.value) || 1;
    current = Math.min(99, Math.max(1, current + delta));
    qtyInput.value = current;
    updateTotalPrice();
}

function updateTotalPrice() {
    const basePriceElem = document.getElementById('basePriceDisplay');
    if (!basePriceElem) return;
    const basePrice = parseFloat(basePriceElem.dataset.basePrice) || 0;

    let extraTotal = 0;
    // Radio & Checkbox options
    const checkedOptions = document.querySelectorAll('#orderCustomizationForm input:checked');
    checkedOptions.forEach(opt => {
        const extra = parseFloat(opt.dataset.extra) || 0;
        extraTotal += extra;
    });

    const qtyInput = document.getElementById('orderQuantity');
    const qty = Math.min(99, Math.max(1, parseInt(qtyInput.value) || 1));
    qtyInput.value = qty;
    const singleItemPrice = basePrice + extraTotal;
    const grandTotal = singleItemPrice * qty;

    const totalDisplay = document.getElementById('totalPriceDisplay');
    if (totalDisplay) {
        totalDisplay.textContent = grandTotal.toLocaleString('vi-VN') + ' ₫';
    }
}

async function handleAddToCart() {
    updateTotalPrice();
    const btn = document.getElementById('addToCartBtn');
    if (!btn || btn.disabled) return;
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span>⏳ Đang thêm vào giỏ...</span>';

    const form = document.getElementById('orderCustomizationForm');
    const foodId = form ? form.dataset.foodId : '';
    const quantity = parseInt(document.getElementById('orderQuantity').value) || 1;
    const optionIds = [];
    document.querySelectorAll('#orderCustomizationForm input:checked').forEach(opt => {
        if (opt.value && opt.value !== '0') {
            optionIds.push(opt.value);
        }
    });

    const ctx = typeof window.CONTEXT_PATH === 'string' ? window.CONTEXT_PATH : '';
    const cartUrl = ctx + '/api/cart/items';

    try {
        const response = await fetch(cartUrl, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ foodId, quantity, optionIds, note: '' })
        });

        if (response.status === 401) {
            btn.innerHTML = '<span>⚠️ Vui lòng đăng nhập</span>';
            btn.style.background = '#dc2626';
            const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
            setTimeout(() => {
                window.location.href = ctx + '/auth/login?reason=session-expired&returnTo=' + returnTo;
            }, 800);
            return;
        }

        if (response.status === 403) {
            alert('Tài khoản nhân viên hoặc quản trị không thể mua hàng. Vui lòng đăng nhập bằng tài khoản khách hàng.');
            btn.innerHTML = originalText;
            btn.style.background = '';
            btn.disabled = false;
            return;
        }

        const data = await response.json();
        if (response.ok && data.success) {
            btn.innerHTML = '<span>✅ Đã thêm món vào giỏ!</span>';
            btn.style.background = '#18794e';
            const badge = document.querySelector('.cart-badge-count');
            if (badge) {
                let currentCount = parseInt(badge.textContent) || 0;
                badge.textContent = data.data?.totalItems ?? (currentCount + quantity);
                badge.style.display = 'inline-flex';
            }
            const dot = document.querySelector('.cart-badge-dot');
            if (dot) dot.style.display = 'block';
            setTimeout(() => {
                btn.innerHTML = originalText;
                btn.style.background = '';
                btn.disabled = false;
            }, 2000);
        } else {
            const msg = data.error?.message || data.message || 'Không thể thêm vào giỏ hàng.';
            alert(msg);
            btn.innerHTML = originalText;
            btn.style.background = '';
            btn.disabled = false;
        }
    } catch (e) {
        alert('Lỗi kết nối khi thêm giỏ hàng: ' + e.message);
        btn.innerHTML = originalText;
        btn.style.background = '';
        btn.disabled = false;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    ['option_sugar', 'option_ice'].forEach(name => {
        const options = document.querySelectorAll(`input[name="${name}"]`);
        if (options.length && ![...options].some(option => option.checked)) options[0].checked = true;
    });
    updateTotalPrice();
});
