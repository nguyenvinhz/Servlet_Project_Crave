package com.foodordering.service;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.CartItemOptionDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.entity.Cart;
import com.foodordering.entity.CartItem;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.mapper.CartItemMapper;
import com.foodordering.mapper.CartMapper;
import com.foodordering.repository.CartItemRepository;
import com.foodordering.repository.CartRepository;
import com.foodordering.validator.CartValidator;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartMapper cartMapper;
    private final CartItemMapper cartItemMapper;

    public CartService() {
        this.cartRepository = new CartRepository();
        this.cartItemRepository = new CartItemRepository();
        this.cartMapper = new CartMapper();
        this.cartItemMapper = new CartItemMapper();
    }

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartMapper = new CartMapper();
        this.cartItemMapper = new CartItemMapper();
    }

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       CartMapper cartMapper, CartItemMapper cartItemMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartMapper = cartMapper != null ? cartMapper : new CartMapper();
        this.cartItemMapper = cartItemMapper != null ? cartItemMapper : new CartItemMapper();
    }

    /**
     * Lấy hoặc tạo giỏ hàng cho khách hàng, chuyển đổi Entity sang DTO qua CartMapper.
     */
    public CartDto getOrCreateCart(String customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartRepository.createCart(customerId);
        }
        List<CartItem> items = cartItemRepository.findItemsByCartId(cart.getCartId());
        CartDto cartDto = cartMapper.toDto(cart, items);
        calculateCartTotals(cartDto);
        return cartDto;
    }

    public CartDto addItem(String customerId, AddToCartRequest request) {
        boolean foodAvailable = cartItemRepository.isFoodAvailable(request.getFoodId());
        if (!foodAvailable) {
            throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món ăn hiện không khả dụng hoặc đã ngừng phục vụ");
        }
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

    public CartDto updateItem(String customerId, UpdateCartItemRequest request) {
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

    /**
     * Tính toán optionTotal, unitPrice và lineTotal cho một mục món ăn.
     */
    public void calculateItemTotals(CartItemDto item) {
        if (item == null) return;
        BigDecimal sumOption = BigDecimal.ZERO;
        if (item.getOptions() != null) {
            for (CartItemOptionDto opt : item.getOptions()) {
                if (opt.getExtraPrice() != null) {
                    sumOption = sumOption.add(opt.getExtraPrice());
                }
            }
        }
        item.setOptionTotal(sumOption);
        BigDecimal base = item.getBasePrice() != null ? item.getBasePrice() : BigDecimal.ZERO;
        BigDecimal unitPrice = base.add(sumOption);
        item.setUnitPrice(unitPrice);
        item.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(Math.max(0, item.getQuantity()))));
    }

    /**
     * Tính toán totalItems và subtotal cho toàn bộ giỏ hàng.
     */
    public void calculateCartTotals(CartDto cart) {
        if (cart == null) return;
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        if (cart.getItems() != null) {
            for (CartItemDto item : cart.getItems()) {
                calculateItemTotals(item);
                count += item.getQuantity();
                if (item.getLineTotal() != null) {
                    sum = sum.add(item.getLineTotal());
                }
            }
        }
        cart.setTotalItems(count);
        cart.setSubtotal(sum);
    }
}
