"use strict";

document.addEventListener("DOMContentLoaded", () => {
    initHeaderCartBadge();
    initCategoryCarousels();
    initQuickAddToCart();
});

function initHeaderCartBadge() {
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
}

/**
 * Tự động chuyển đổi hình ảnh món ăn theo từng danh mục sau một khoảng thời gian
 */
function initCategoryCarousels() {
    const carousels = document.querySelectorAll("[data-category-carousel]");
    if (!carousels.length) return;

    carousels.forEach((container, containerIndex) => {
        const images = Array.from(container.querySelectorAll(".category-carousel-img"));
        if (images.length <= 1) return;

        let currentIndex = images.findIndex(img => img.classList.contains("active"));
        if (currentIndex < 0) {
            currentIndex = 0;
            images[0].classList.add("active");
        }

        // Nếu một ảnh lỗi, ẩn đi và loại khỏi danh sách luân chuyển
        images.forEach(img => {
            img.addEventListener("error", () => {
                const wasActive = img.classList.contains("active");
                img.classList.remove("active");
                img.style.display = "none";
                const remaining = Array.from(container.querySelectorAll(".category-carousel-img"))
                    .filter(el => el.style.display !== "none");
                if (wasActive && remaining.length > 0) {
                    currentIndex = currentIndex % remaining.length;
                    remaining[currentIndex].classList.add("active");
                }
            });
        });

        let isPaused = false;
        const nextSlide = () => {
            if (isPaused) return;
            const currentImages = Array.from(container.querySelectorAll(".category-carousel-img"))
                .filter(el => el.style.display !== "none");
            if (currentImages.length <= 1) return;

            currentImages[currentIndex].classList.remove("active");
            currentIndex = (currentIndex + 1) % currentImages.length;
            currentImages[currentIndex].classList.add("active");
        };

        // Chạy chuyển ảnh mỗi 3.5 giây, chia lệch thời gian giữa các danh mục để tạo cảm giác tự nhiên
        const staggerDelay = (containerIndex % 6) * 500;
        setTimeout(() => {
            setInterval(nextSlide, 3500);
        }, staggerDelay);

        // Tạm dừng khi rê chuột vào để người dùng xem rõ món
        const tile = container.closest(".craving-tile");
        if (tile) {
            tile.addEventListener("mouseenter", () => { isPaused = true; });
            tile.addEventListener("mouseleave", () => { isPaused = false; });
        }
    });
}

/**
 * Xử lý thêm nhanh món ăn (tùy chọn cơ bản) khi bấm dấu + trên thẻ món ăn
 */
function initQuickAddToCart() {
    document.addEventListener("click", async (e) => {
        const btn = e.target.closest("[data-quick-add]");
        if (!btn) return;

        e.preventDefault();
        e.stopPropagation();

        const foodId = btn.dataset.quickAdd;
        const foodName = btn.dataset.foodName || "Món ăn";
        if (!foodId || btn.disabled) return;

        btn.disabled = true;
        btn.classList.add("adding");
        const originalHtml = btn.innerHTML;

        btn.innerHTML = `<svg class="spin-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>`;

        const contextPath = document.body.dataset.contextPath || window.CONTEXT_PATH || "";
        try {
            const response = await fetch(`${contextPath}/api/cart/items`, {
                method: "POST",
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify({
                    foodId: foodId,
                    quantity: 1,
                    optionIds: [],
                    note: ""
                })
            });

            if (response.status === 401) {
                showToast("Vui lòng đăng nhập để thêm món vào giỏ hàng!", "error");
                btn.innerHTML = originalHtml;
                btn.classList.remove("adding");
                btn.disabled = false;
                const returnTo = encodeURIComponent(window.location.pathname + window.location.search);
                setTimeout(() => {
                    window.location.href = `${contextPath}/auth/login?reason=session-expired&returnTo=${returnTo}`;
                }, 900);
                return;
            }

            if (response.status === 403) {
                showToast("Tài khoản quản trị/nhân viên không thể mua hàng. Vui lòng dùng tài khoản khách hàng.", "error");
                btn.innerHTML = originalHtml;
                btn.classList.remove("adding");
                btn.disabled = false;
                return;
            }

            const data = await response.json();
            if (response.ok && data.success) {
                btn.classList.remove("adding");
                btn.classList.add("success");
                btn.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M20 6 9 17l-5-5"/></svg>`;

                // Cập nhật số lượng trên icon giỏ hàng ở header
                const cartBtn = document.querySelector(".header-cart-icon-btn");
                const badge = document.querySelector(".cart-badge-count");
                if (badge) {
                    const count = Number(data.data?.totalItems) || (Number(badge.textContent) || 0) + 1;
                    badge.textContent = String(count);
                    badge.style.display = count > 0 ? "inline-flex" : "none";
                }
                if (cartBtn) {
                    cartBtn.classList.remove("cart-bump");
                    void cartBtn.offsetWidth;
                    cartBtn.classList.add("cart-bump");
                }

                showToast(`Đã thêm “${foodName}” (tùy chọn cơ bản) vào giỏ hàng!`, "success");

                setTimeout(() => {
                    btn.classList.remove("success");
                    btn.innerHTML = originalHtml;
                    btn.disabled = false;
                }, 1200);
            } else {
                const msg = data.error?.message || data.message || "Không thể thêm món vào giỏ hàng.";
                showToast(msg, "error");
                btn.innerHTML = originalHtml;
                btn.classList.remove("adding");
                btn.disabled = false;
            }
        } catch (err) {
            showToast("Lỗi kết nối khi thêm giỏ hàng: " + err.message, "error");
            btn.innerHTML = originalHtml;
            btn.classList.remove("adding");
            btn.disabled = false;
        }
    });
}

function showToast(message, type = "info") {
    let container = document.querySelector(".crave-toast-container");
    if (!container) {
        container = document.createElement("div");
        container.className = "crave-toast-container";
        document.body.appendChild(container);
    }
    const toast = document.createElement("div");
    toast.className = `crave-toast ${type}`;
    const icon = type === "success" ? "✓ " : (type === "error" ? "⚠️ " : "ℹ️ ");
    toast.innerHTML = `<span><strong>${icon}</strong>${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = "0";
        toast.style.transform = "translateY(10px)";
        toast.style.transition = "all 0.25s ease";
        setTimeout(() => toast.remove(), 250);
    }, 3200);
}

function escapeHtml(text) {
    if (!text) return "";
    return text.replace(/[&<>"']/g, m => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[m]);
}
