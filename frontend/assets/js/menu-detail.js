
function changeQuantity(delta) {
    const qtyInput = document.getElementById('orderQuantity');
    let current = parseInt(qtyInput.value) || 1;
    current = Math.min(100, Math.max(1, current + delta));
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
    const qty = Math.min(100, Math.max(1, parseInt(qtyInput.value) || 1));
    qtyInput.value = qty;
    const singleItemPrice = basePrice + extraTotal;
    const grandTotal = singleItemPrice * qty;

    const totalDisplay = document.getElementById('totalPriceDisplay');
    if (totalDisplay) {
        totalDisplay.textContent = grandTotal.toLocaleString('vi-VN') + ' ₫';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('orderCustomizationForm');
    if (form) {
        form.addEventListener('change', updateTotalPrice);
        form.addEventListener('input', updateTotalPrice);
    }
    updateTotalPrice();
});
