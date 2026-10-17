function changeQuantity(delta) {
    const quantity = document.getElementById('orderQuantity');
    quantity.value = Math.min(99, Math.max(1, (parseInt(quantity.value) || 1) + delta));
    updateTotalPrice();
}

function updateTotalPrice() {
    const base = document.getElementById('basePriceDisplay');
    if (!base) return;
    let unitPrice = Number(base.dataset.basePrice) || 0;
    document.querySelectorAll('#orderCustomizationForm input:checked').forEach(option => {
        unitPrice += Number(option.dataset.extra) || 0;
    });
    const quantity = document.getElementById('orderQuantity');
    quantity.value = Math.min(99, Math.max(1, parseInt(quantity.value) || 1));
    document.getElementById('totalPriceDisplay').textContent = (unitPrice * Number(quantity.value)).toLocaleString('vi-VN') + ' ₫';
}

function handleAddToCart() {
    const form = document.getElementById('orderCustomizationForm');
    if (!form || !form.reportValidity()) return;
    updateTotalPrice();
    form.querySelectorAll('input[data-submitted-option]').forEach(input => input.remove());
    form.querySelectorAll('input:checked').forEach(option => {
        if (!option.value || option.value === '0') return;
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = 'optionIds';
        input.value = option.value;
        input.dataset.submittedOption = '';
        form.append(input);
    });
    document.getElementById('addToCartBtn').disabled = true;
    form.submit();
}

document.addEventListener('DOMContentLoaded', () => {
    ['option_sugar', 'option_ice'].forEach(name => {
        const options = document.querySelectorAll('input[name="' + name + '"]');
        if (options.length && ![...options].some(option => option.checked)) options[0].checked = true;
    });
    updateTotalPrice();
});