"use strict";

document.addEventListener("DOMContentLoaded", () => {
    initHeaderCartBadge();
    initCategoryCarousels();
    initQuickAddToCart();
});

function initHeaderCartBadge() {
    const countField = document.getElementById('pageCartCount');
    const count = Number(countField ? countField.value : document.querySelector('.cart-badge-count')?.textContent) || 0;
    updateHeaderCartBadge(count);
    const flash = document.getElementById('cartFlash');
    if (flash?.dataset.message) showToast(flash.dataset.message, flash.dataset.type || 'success');
}

function updateHeaderCartBadge(count) {
    const countField = document.getElementById('pageCartCount');
    if (countField) countField.value = String(count);
    document.querySelectorAll('.cart-badge-count').forEach(badge => {
        badge.textContent = String(count);
        badge.style.display = count > 0 ? 'inline-flex' : 'none';
    });
    document.querySelector('.header-cart-icon-btn')?.setAttribute('aria-label', 'Giỏ hàng, ' + count + ' món');
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
    document.addEventListener('click', async event => {
        const button = event.target.closest('[data-quick-add]');
        if (!button || button.disabled || !button.dataset.quickAdd) return;
        event.preventDefault();
        event.stopPropagation();
        // Process one cart change at a time because the result is stored in the session flash.
        const buttons = [...document.querySelectorAll('[data-quick-add]:not(:disabled)')];
        buttons.forEach(item => { item.disabled = true; });
        button.setAttribute('aria-busy', 'true');
        const contextPath = document.body.dataset.contextPath || '';
        const currentPath = window.location.pathname.slice(contextPath.length);
        const fields = { action: 'add', foodId: button.dataset.quickAdd, quantity: '1', note: '', returnTo: currentPath + window.location.search };
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 15000);
        try {
            const response = await fetch(contextPath + '/cart/action', {
                method: 'POST', credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
                headers: { 'Accept': 'text/html', 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: new URLSearchParams(fields)
            });
            if (response.redirected && new URL(response.url).pathname === contextPath + '/auth/login') {
                window.location.assign(response.url);
                return;
            }
            if (!response.ok) throw new Error('Cart request failed');
            const result = new DOMParser().parseFromString(await response.text(), 'text/html');
            const countField = result.getElementById('pageCartCount');
            const flash = result.getElementById('cartFlash');
            const count = Number(countField?.value);
            if (!countField || !Number.isInteger(count) || count < 0 || !flash?.dataset.message
                    || !['success', 'error'].includes(flash.dataset.type)) {
                throw new Error('Invalid cart response');
            }
            updateHeaderCartBadge(count);
            showToast(flash.dataset.message, flash.dataset.type);
        } catch {
            showToast('Chưa xác nhận được kết quả thêm món. Vui lòng kiểm tra giỏ hàng rồi thử lại.', 'error');
        } finally {
            clearTimeout(timer);
            buttons.forEach(item => { item.disabled = false; });
            button.removeAttribute('aria-busy');
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
