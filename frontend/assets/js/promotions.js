"use strict";

document.addEventListener("DOMContentLoaded", () => {
    const loading = document.getElementById("promosLoadingState");
    const grid = document.getElementById("promosGridContainer");
    const empty = document.getElementById("promosEmptyState");
    const error = document.getElementById("promosErrorState");
    const feedback = document.getElementById("promosFeedback");
    const contextPath = document.body.dataset.contextPath || "";
    const price = value => `${new Intl.NumberFormat("vi-VN").format(Number(value) || 0)}\u00A0đ`;
    const formatCurrencyText = text => {
        if (!text) return "";
        return String(text).replace(/(\d+[\d.,]*)\s*([đ₫Đ]|VNĐ|VND|vnđ|vnd)/g, "$1\u00A0$2");
    };
    const element = (tag, className, value) => {
        const node = document.createElement(tag);
        node.className = className;
        if (value !== undefined) node.textContent = value;
        return node;
    };
    let pending = false;

    async function load() {
        if (pending) return;
        pending = true;
        loading.style.display = "block";
        grid.style.display = "none";
        empty.style.display = "none";
        error.hidden = true;
        feedback.textContent = "";
        try {
            const response = await fetch(`${contextPath}/api/promotions/active`, {headers: {Accept: "application/json"}, credentials: "same-origin"});
            const result = await response.json();
            if (!response.ok || result.success !== true || !Array.isArray(result.data)) {
                throw new Error(result.error?.message || "Không thể tải danh sách khuyến mãi. Vui lòng thử lại.");
            }
            grid.replaceChildren();
            result.data.forEach(promotion => {
                const code = String(promotion.code || "");
                const discount = promotion.discountType === "PERCENT" ? `Giảm ${Number(promotion.discountValue) || 0}%` : `Giảm ${price(promotion.discountValue)}`;
                const terms = [Number(promotion.minimumOrderValue) > 0 ? `Đơn tối thiểu ${price(promotion.minimumOrderValue)}` : "Không giới hạn đơn tối thiểu", Number(promotion.maximumDiscount) > 0 ? `Giảm tối đa ${price(promotion.maximumDiscount)}` : ""].filter(Boolean).join(" · ");
                const card = element("article", "promo-ticket-card");
                const content = element("div", "promo-ticket-content");
                const top = element("div", "promo-ticket-top");
                top.append(element("span", "promo-ticket-code", code), element("span", "promo-ticket-value", discount));
                content.append(top, element("h2", "promo-ticket-name", promotion.name || "Ưu đãi cho bạn"));
                if (promotion.description) content.append(element("p", "promo-ticket-desc", formatCurrencyText(promotion.description)));
                content.append(element("p", "promo-ticket-desc", formatCurrencyText(terms)));
                const actions = element("div", "promo-ticket-bottom");
                const copy = element("button", "btn-copy-code", "Sao chép mã");
                copy.type = "button";
                copy.addEventListener("click", async () => {
                    try {
                        if (!navigator.clipboard) throw new Error("Clipboard unavailable");
                        await navigator.clipboard.writeText(code);
                        feedback.textContent = `Đã sao chép mã ${code}.`;
                        copy.textContent = "Đã sao chép ✓";
                    } catch (_) { feedback.textContent = `Mã ưu đãi: ${code}. Bạn có thể sao chép và nhập mã trong giỏ hàng.`; }
                });
                const use = element("a", "btn-apply-direct", "Dùng ngay");
                use.href = `${contextPath}/cart?code=${encodeURIComponent(code)}`;
                actions.append(copy, use);
                card.append(content, actions);
                grid.append(card);
            });
            grid.style.display = result.data.length ? "grid" : "none";
            empty.style.display = result.data.length ? "none" : "block";
        } catch (failure) {
            document.getElementById("promosErrorMessage").textContent = failure.message || "Lỗi kết nối. Vui lòng thử lại.";
            error.hidden = false;
        } finally { pending = false; loading.style.display = "none"; }
    }
    document.getElementById("promosRetry").addEventListener("click", load);
    load();
});
