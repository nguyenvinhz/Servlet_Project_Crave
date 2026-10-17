"use strict";

// Forms use normal Servlet requests. JavaScript only handles local interactions.
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-location]").forEach(button => {
        button.addEventListener("click", () => window.location.assign(button.dataset.location));
    });
    const otpForm = document.querySelector("form[data-code-url]");
    if (otpForm) {
        const fields = otpForm.querySelector("[data-otp-fields]");
        const edit = document.getElementById("otp-edit");
        edit?.addEventListener("click", () => {
            fields.hidden = true;
            fields.disabled = true;
            otpForm.action = otpForm.dataset.codeUrl;
            otpForm.querySelector("[name=otpActive]").value = "false";
            document.getElementById("otp-submit").textContent = "Gửi mã xác nhận";
            const current = otpForm.elements.namedItem("currentPassword");
            if (current) current.required = true;
            const identity = otpForm.elements.namedItem(current ? "currentPassword" : "email");
            identity?.focus();
        });
    }

    document.querySelectorAll("form").forEach(form => {
        form.addEventListener("submit", event => {
            if (event.submitter?.formNoValidate) return;
            const password = form.elements.namedItem("password");
            const confirmation = form.elements.namedItem("confirmPassword");
            if (password && confirmation && !confirmation.matches(":disabled")) {
                confirmation.setCustomValidity(password.value === confirmation.value ? "" : "Mật khẩu nhập lại chưa khớp.");
                if (!confirmation.reportValidity()) event.preventDefault();
            }
        });
        const confirmation = form.elements.namedItem("confirmPassword");
        confirmation?.addEventListener("input", () => confirmation.setCustomValidity(""));
        form.elements.namedItem("password")?.addEventListener("input", () => confirmation?.setCustomValidity(""));
    });

    document.querySelectorAll("form[data-confirm]").forEach(form => {
        form.addEventListener("submit", event => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
});
