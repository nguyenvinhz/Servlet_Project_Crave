"use strict";

(function () {
    const contextPath = document.body.dataset.contextPath || "";
    const cartSignalKey = "crave:cart-updated";
    let cart = null;
    let revision = 0;
    let pendingRead = null;
    let remoteReadQueued = false;

    function renderCartState() {
        const items = Array.isArray(cart?.items) ? cart.items : [];
        const count = Number(cart?.totalItems) || items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
        document.querySelectorAll(".cart-badge-count").forEach(badge => {
            badge.textContent = String(count);
            badge.style.display = count > 0 ? "inline-flex" : "none";
        });
        document.querySelectorAll(".header-cart-icon-btn").forEach(button => {
            button.setAttribute("aria-label", `Giỏ hàng, ${count} món`);
        });
        const quantities = new Map();
        items.forEach(item => quantities.set(String(item.foodId), (quantities.get(String(item.foodId)) || 0) + (Number(item.quantity) || 0)));
        document.querySelectorAll(".clean-food-card[data-food-id]").forEach(card => {
            const quantity = quantities.get(card.dataset.foodId) || 0;
            card.classList.toggle("food-card-in-cart", quantity > 0);
            const label = card.querySelector(".cart-added-label");
            if (label) {
                label.hidden = quantity === 0;
                label.textContent = `✓ Trong giỏ: ${quantity}`;
            }
            const action = card.querySelector("[data-cart-add]");
            if (action) {
                action.setAttribute("aria-label", quantity ? `${action.dataset.foodName}, đã có ${quantity} phần trong giỏ. Thêm món` : `Chọn món ${action.dataset.foodName}`);
                action.querySelector("svg path")?.setAttribute("d", quantity ? "m5 12 4 4L19 6" : "M12 5v14M5 12h14");
            }
        });
        const form = document.getElementById("orderCustomizationForm");
        const status = document.getElementById("foodCartStatus");
        if (form && status) {
            const quantity = quantities.get(form.dataset.foodId) || 0;
            status.hidden = quantity === 0;
            status.textContent = `✓ Đã có ${quantity} phần món này trong giỏ hàng.`;
        }
    }

    function update(nextCart, { broadcast = true, source = "local" } = {}) {
        revision++;
        cart = nextCart;
        renderCartState();
        document.dispatchEvent(new CustomEvent("crave:cart-change", { detail: { cart, source } }));
        if (broadcast) {
            // Only invalidate other tabs; each tab fetches its own authenticated cart.
            try { localStorage.setItem(cartSignalKey, `${Date.now()}:${Math.random()}`); } catch (_) { }
        }
        return cart;
    }

    async function refresh({ source = "refresh" } = {}) {
        if (pendingRead) {
            if (source === "remote") remoteReadQueued = true;
            return pendingRead;
        }
        const startedAtRevision = revision;
        pendingRead = (async () => {
            try {
                const response = await fetch(`${contextPath}/api/cart`, { credentials: "same-origin", headers: { Accept: "application/json" }, cache: "no-store" });
                if (!response.ok) return cart;
                const result = await response.json();
                // An earlier GET must never overwrite a later add/update/delete response.
                if (result.success && result.data && startedAtRevision === revision) {
                    update(result.data, { broadcast: false, source });
                }
            } catch (_) { /* Keep the last confirmed state when the connection is unavailable. */ }
            return cart;
        })();
        try { return await pendingRead; } finally {
            pendingRead = null;
            if (remoteReadQueued) {
                remoteReadQueued = false;
                refresh({ source: "remote" });
            }
        }
    }

    window.CraveCart = { update, refresh, get cart() { return cart; } };

    window.CravePagination = {
        create({ container, totalItems, pageSize, label, onChange }) {
            if (!container) { onChange(1); return; }
            const pages = Math.max(1, Math.ceil(totalItems / pageSize));
            let page = 1;
            function render() {
                container.hidden = totalItems <= pageSize;
                container.replaceChildren();
                if (container.hidden) return;
                const summary = document.createElement("span");
                summary.className = "pagination-summary";
                summary.textContent = `${(page - 1) * pageSize + 1}–${Math.min(page * pageSize, totalItems)} / ${totalItems} ${label}`;
                const controls = document.createElement("div");
                controls.className = "pagination-controls";
                const addButton = (value, text, ariaLabel, disabled = false) => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "pagination-btn" + (value === page && /^\d+$/.test(text) ? " active" : "");
                    button.textContent = text;
                    button.setAttribute("aria-label", ariaLabel);
                    if (value === page && /^\d+$/.test(text)) button.setAttribute("aria-current", "page");
                    button.disabled = disabled;
                    button.addEventListener("click", () => {
                        page = value;
                        onChange(page);
                        render();
                        container.querySelector('[aria-current="page"]')?.focus({ preventScroll: true });
                    });
                    controls.append(button);
                };
                addButton(page - 1, "←", "Trang trước", page === 1);
                const visible = [...new Set([1, ...Array.from({ length: 5 }, (_, i) => page - 2 + i).filter(value => value > 0 && value <= pages), pages])].sort((a, b) => a - b);
                visible.forEach((value, index) => {
                    if (index > 0 && value > visible[index - 1] + 1) {
                        const ellipsis = document.createElement("span");
                        ellipsis.className = "pagination-ellipsis";
                        ellipsis.textContent = "…";
                        controls.append(ellipsis);
                    }
                    addButton(value, String(value), `Trang ${value}`);
                });
                addButton(page + 1, "→", "Trang sau", page === pages);
                container.append(summary, controls);
            }
            onChange(page);
            render();
        }
    };

    function initCategoryGalleries() {
        const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
        const galleries = [...document.querySelectorAll("[data-category-gallery]")].map(media => ({
            image: media.querySelector("img"),
            photos: [...new Set([...media.querySelectorAll("[data-photo-src]")].map(node => node.dataset.photoSrc).filter(Boolean))],
            index: 0,
            failed: new Set()
        })).filter(gallery => gallery.image && gallery.photos.length > 1);
        if (!galleries.length) return;
        const advance = () => {
            if (document.hidden || reducedMotion.matches) return;
            galleries.forEach(gallery => {
                gallery.index = (gallery.index + 1) % gallery.photos.length;
                const source = gallery.photos[gallery.index];
                if (gallery.failed.has(source)) return;
                const preload = new Image();
                preload.onload = () => {
                    if (document.hidden || reducedMotion.matches) return;
                    gallery.image.classList.add("photo-changing");
                    window.setTimeout(() => {
                        gallery.image.src = source;
                        gallery.image.classList.remove("photo-changing");
                    }, 180);
                };
                preload.onerror = () => gallery.failed.add(source);
                preload.src = source;
            });
        };
        let timer = window.setInterval(advance, 4500);
        window.addEventListener("pagehide", () => window.clearInterval(timer));
        window.addEventListener("pageshow", event => { if (event.persisted) timer = window.setInterval(advance, 4500); });
    }

    function initQuickAdd() {
        const feedback = document.getElementById("menuCartFeedback");
        document.querySelectorAll("[data-cart-add]").forEach(action => {
            action.addEventListener("click", async event => {
                if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
                if (document.body.dataset.accountType !== "CUSTOMER") return;
                event.preventDefault();
                if (action.getAttribute("aria-busy") === "true") return;
                action.setAttribute("aria-busy", "true");
                const foodId = action.closest("[data-food-id]").dataset.foodId;
                try {
                    const detailResponse = await fetch(`${contextPath}/api/foods/${encodeURIComponent(foodId)}`, { credentials: "same-origin", headers: { Accept: "application/json" } });
                    const detail = await detailResponse.json();
                    if (!detailResponse.ok || !detail.success) throw new Error(detail.error?.message || "Không thể tải món ăn.");
                    if (detail.data?.options?.length) {
                        window.location.assign(action.href);
                        return;
                    }
                    const response = await fetch(`${contextPath}/api/cart/items`, {
                        method: "POST", credentials: "same-origin",
                        headers: { "Content-Type": "application/json", Accept: "application/json" },
                        body: JSON.stringify({ foodId, quantity: 1, optionIds: [], note: "" })
                    });
                    if (response.status === 401) {
                        window.location.assign(`${contextPath}/auth/login?reason=session-expired&returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`);
                        return;
                    }
                    const result = await response.json();
                    if (!response.ok || !result.success || !result.data) throw new Error(result.error?.message || "Không thể thêm món vào giỏ hàng.");
                    update(result.data);
                    if (feedback) {
                        feedback.classList.remove("is-error");
                        feedback.textContent = `Đã thêm ${action.dataset.foodName} vào giỏ hàng.`;
                    }
                } catch (error) {
                    if (feedback) {
                        feedback.classList.add("is-error");
                        feedback.textContent = error.message || "Lỗi kết nối. Vui lòng thử lại.";
                    }
                } finally { action.removeAttribute("aria-busy"); }
            });
        });
    }

    document.addEventListener("DOMContentLoaded", () => {
        initCategoryGalleries();
        initQuickAdd();
        const signedInCustomer = Boolean(document.querySelector(".header-cart-icon-btn") && document.getElementById("logoutForm"));
        if (!signedInCustomer) return;
        if (!document.getElementById("cartItemsList")) refresh();
        window.addEventListener("storage", event => {
            if (event.key === cartSignalKey) refresh({ source: "remote" });
        });
        window.addEventListener("focus", () => refresh({ source: "remote" }));
        window.addEventListener("pageshow", event => { if (event.persisted) refresh({ source: "remote" }); });
        document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh({ source: "remote" }); });
    });
})();
