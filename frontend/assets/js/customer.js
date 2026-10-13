"use strict";

document.addEventListener("DOMContentLoaded", () => {
    initHeaderCartBadge();
    initCategoryCarousels();
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
