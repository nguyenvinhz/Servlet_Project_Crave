"use strict";

// Forms use normal browser navigation. JavaScript only adds optional confirmation.
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    document.getElementById("logoutForm")?.addEventListener("submit", () => {
        try { sessionStorage.removeItem("crave_applied_voucher"); } catch (_) { }
    });
});
