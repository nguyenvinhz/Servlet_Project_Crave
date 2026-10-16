"use strict";
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("form[data-confirm]").forEach(form => {
        form.addEventListener("submit", event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    const editor = document.getElementById("cartItemEditorForm");
    if (!editor) return;
    const quantity = document.getElementById("cartEditorQuantity");
    const total = document.getElementById("cartEditorTotal");
    const updatePrice = () => {
        let price = Number(editor.dataset.basePrice) || 0;
        editor.querySelectorAll("input[data-extra]:checked").forEach(option => {
            price += Number(option.dataset.extra) || 0;
        });
        total.textContent = new Intl.NumberFormat("vi-VN").format(price * (Number(quantity.value) || 0)) + " ₫";
    };
    editor.addEventListener("input", updatePrice);
    editor.addEventListener("change", updatePrice);
    updatePrice();
});