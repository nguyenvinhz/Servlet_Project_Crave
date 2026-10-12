"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const cartButton = document.querySelector(".header-cart-icon-btn");
    if (!cartButton || !document.getElementById("logoutForm") || document.getElementById("cartItemsList")) return;
    const contextPath = document.body.dataset.contextPath || "";
    fetch(`${contextPath}/api/cart`, {credentials: "same-origin", headers: {Accept: "application/json"}})
        .then(response => response.ok ? response.json() : null)
        .then(result => {
            if (result?.success !== true) return;
            const count = Number(result.data?.totalItems) || 0;
            const badge = cartButton.querySelector(".cart-badge-count");
            if (badge) {
                badge.textContent = String(count);
                badge.style.display = count > 0 ? "inline-flex" : "none";
            }
            cartButton.setAttribute("aria-label", `Giỏ hàng, ${count} món`);
        })
        .catch(() => {});
});
