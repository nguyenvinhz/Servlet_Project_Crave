package com.foodordering.service;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.entity.Cart;
import com.foodordering.entity.CartItem;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.repository.CartItemRepository;
import com.foodordering.repository.CartRepository;
import com.foodordering.validator.CartValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service xử lý nghiệp vụ giỏ hàng (Cart vertical slice do Ung Văn Trí phụ trách).
 * Tuyệt đối tuân thủ nguyên tắc không tin cậy dữ liệu giá hoặc tổng tiền từ client.
 */
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartService() {
        this.cartRepository = new CartRepository();
        this.cartItemRepository = new CartItemRepository();
    }

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Lấy thông tin giỏ hàng hiện tại của khách hàng.
     * Nếu khách hàng chưa có giỏ hàng, hệ thống sẽ tự động khởi tạo giỏ hàng mới.
     */
    public CartDto getOrCreateCart(String customerId) {
        CartValidator.validateCustomerId(customerId);
        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartRepository.createCart(customerId);
        }

        List<CartItemDto> items = cartItemRepository.findItemsByCartId(cart.getCartId());
        CartDto cartDto = new CartDto(cart.getCartId(), customerId);
        cartDto.setItems(items);
        return cartDto;
    }

    /**
     * Thêm món vào giỏ hàng:
     * - Kiểm tra món có đang phục vụ (AVAILABLE) không.
     * - Kiểm tra các tùy chọn đính kèm có thuộc về món đó và đang ACTIVE không.
     * - Nếu món và danh sách tùy chọn hoàn toàn trùng khớp với món đã có trong giỏ, tiến hành cộng dồn số lượng.
     * - Ngược lại, tạo một mục món mới.
     */
    public CartDto addItem(String customerId, AddToCartRequest request) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateAddToCart(request);

        // 1. Kiểm tra trạng thái món ăn trong DB
        boolean foodAvailable = cartItemRepository.isFoodAvailable(request.getFoodId());
        if (!foodAvailable) {
            throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món ăn hiện không khả dụng hoặc đã ngừng phục vụ");
        }

        // 2. Kiểm tra các tùy chọn (options) có hợp lệ không
        List<String> rawOptions = request.getOptionIds() != null ? request.getOptionIds() : Collections.emptyList();
        if (!rawOptions.isEmpty()) {
            List<String> validOptions = cartItemRepository.getValidOptionIdsForFood(request.getFoodId(), rawOptions);
            if (validOptions.size() != rawOptions.size()) {
                throw new BadRequestException(ErrorCode.INVALID_OPTION, "Một số tùy chọn món không hợp lệ hoặc đã ngừng áp dụng");
            }
        }

        // 3. Lấy hoặc tạo giỏ hàng
        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartRepository.createCart(customerId);
        }

        // 4. Kiểm tra món trùng (cùng foodId và cùng tập hợp tùy chọn)
        CartItem duplicateItem = cartItemRepository.findDuplicateItem(cart.getCartId(), request.getFoodId(), rawOptions);
        if (duplicateItem != null) {
            int newQty = duplicateItem.getQuantity() + request.getQuantity();
            if (newQty > 100) {
                throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Tổng số lượng cho món này trong giỏ vượt quá giới hạn 100 phần");
            }
            cartItemRepository.updateQuantity(duplicateItem.getCartItemId(), newQty);
            if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
                cartItemRepository.updateNote(duplicateItem.getCartItemId(), request.getNote().trim());
            }
        } else {
            CartItem newItem = new CartItem();
            newItem.setCartId(cart.getCartId());
            newItem.setFoodId(request.getFoodId());
            newItem.setQuantity(request.getQuantity());
            newItem.setNote(request.getNote() != null ? request.getNote().trim() : null);
            cartItemRepository.insertItemWithOptions(newItem, rawOptions);
        }

        return getOrCreateCart(customerId);
    }

    /**
     * Cập nhật số lượng hoặc ghi chú của món trong giỏ hàng.
     * Nếu số lượng mới bằng 0, mục món sẽ tự động bị xóa khỏi giỏ.
     */
    public CartDto updateItem(String customerId, UpdateCartItemRequest request) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateUpdateCartItem(request);

        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            throw new ResourceNotFoundException(ErrorCode.CART_NOT_FOUND, "Không tìm thấy giỏ hàng của bạn");
        }

        CartItem item = cartItemRepository.findById(request.getCartItemId());
        if (item == null || !cart.getCartId().equals(item.getCartId())) {
            throw new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND, "Món không tồn tại trong giỏ hàng của bạn");
        }

        if (request.getQuantity() == 0) {
            cartItemRepository.deleteItem(item.getCartItemId());
        } else {
            cartItemRepository.updateQuantity(item.getCartItemId(), request.getQuantity());
            if (request.getNote() != null) {
                cartItemRepository.updateNote(item.getCartItemId(), request.getNote().trim());
            }
        }

        return getOrCreateCart(customerId);
    }

    /**
     * Xóa một món ra khỏi giỏ hàng.
     */
    public CartDto removeItem(String customerId, String cartItemId) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateCartItemId(cartItemId);

        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            throw new ResourceNotFoundException(ErrorCode.CART_NOT_FOUND, "Không tìm thấy giỏ hàng của bạn");
        }

        CartItem item = cartItemRepository.findById(cartItemId);
        if (item == null || !cart.getCartId().equals(item.getCartId())) {
            throw new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND, "Món không tồn tại trong giỏ hàng của bạn");
        }

        cartItemRepository.deleteItem(cartItemId);
        return getOrCreateCart(customerId);
    }

    /**
     * Xóa toàn bộ món trong giỏ hàng.
     */
    public CartDto clearCart(String customerId) {
        CartValidator.validateCustomerId(customerId);
        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart != null) {
            cartItemRepository.clearCart(cart.getCartId());
        }
        return getOrCreateCart(customerId);
    }

    /**
     * Tính toán subtotal giỏ hàng chính xác từ phía máy chủ để phục vụ cho các bước thanh toán / tạo đơn.
     */
    public BigDecimal getVerifiedSubtotal(String customerId) {
        CartDto cart = getOrCreateCart(customerId);
        return cart.getSubtotal();
    }
}
