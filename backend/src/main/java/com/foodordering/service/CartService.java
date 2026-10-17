package com.foodordering.service;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.CartItemOptionDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.entity.Cart;
import com.foodordering.entity.CartItem;
import com.foodordering.entity.CartItemOption;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.dao.CartItemDAO;
import com.foodordering.dao.CartDAO;
import com.foodordering.validator.CartValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CartService {

    private final CartDAO cartRepository;
    private final CartItemDAO cartItemRepository;

    public CartService() {
        this.cartRepository = new CartDAO();
        this.cartItemRepository = new CartItemDAO();
    }

    public CartService(CartDAO cartRepository, CartItemDAO cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    /**
     * Lấy hoặc tạo giỏ hàng và chuẩn bị dữ liệu hiển thị cho khách hàng.
     */
    public CartDto getOrCreateCart(String customerId) {
        CartValidator.validateCustomerId(customerId);
        Cart cart = cartRepository.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartRepository.createCart(customerId);
        }
        List<CartItem> items = cartItemRepository.findItemsByCartId(cart.getCartId());
        CartDto cartDto = createCartDto(cart, items);
        calculateCartTotals(cartDto);
        return cartDto;
    }

    public CartDto addItem(String customerId, AddToCartRequest request) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateAddToCart(request);

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
        } else if (request.getOptionIds() != null) {
            if (!cartItemRepository.isFoodAvailable(item.getFoodId())) {
                throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món ăn hiện không khả dụng hoặc đã ngừng phục vụ");
            }
            List<String> validOptions = cartItemRepository.getValidOptionIdsForFood(item.getFoodId(), request.getOptionIds());
            if (validOptions.size() != request.getOptionIds().size()) {
                throw new BadRequestException(ErrorCode.INVALID_OPTION, "Một số tùy chọn món không hợp lệ hoặc đã ngừng áp dụng");
            }
            cartItemRepository.updateItemWithOptions(item.getCartItemId(), cart.getCartId(), request.getQuantity(),
                    request.getNote() == null ? null : request.getNote().trim(), request.getOptionIds());
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

    private CartDto createCartDto(Cart cart, List<CartItem> items) {
        if (cart == null) {
            return null;
        }
        CartDto dto = new CartDto();
        dto.setCartId(cart.getCartId());
        dto.setCustomerId(cart.getCustomer() != null ? cart.getCustomer().getId() : cart.getCustomerId());

        List<CartItemDto> itemDtos = new ArrayList<>();
        if (items != null) {
            for (CartItem item : items) {
                if (item == null) {
                    continue;
                }
                CartItemDto itemDto = new CartItemDto();
                itemDto.setCartItemId(item.getCartItemId());
                itemDto.setCartId(item.getCart() != null ? item.getCart().getCartId() : item.getCartId());
                itemDto.setQuantity(item.getQuantity());
                itemDto.setNote(item.getNote());

                Food food = item.getFood();
                if (food != null) {
                    itemDto.setFoodId(food.getFoodId());
                    itemDto.setFoodName(food.getName());
                    itemDto.setImageUrl(food.getImageUrl());
                    itemDto.setBasePrice(food.getBasePrice() != null ? food.getBasePrice() : BigDecimal.ZERO);
                } else {
                    itemDto.setFoodId(item.getFoodId());
                    itemDto.setBasePrice(BigDecimal.ZERO);
                }

                List<CartItemOptionDto> optionDtos = new ArrayList<>();
                if (item.getItemOptions() != null) {
                    for (CartItemOption itemOption : item.getItemOptions()) {
                        if (itemOption == null) {
                            continue;
                        }
                        CartItemOptionDto optionDto = new CartItemOptionDto();
                        FoodOption option = itemOption.getOption();
                        if (option != null) {
                            optionDto.setOptionId(option.getOptionId());
                            optionDto.setName(option.getName());
                            optionDto.setOptionType(option.getOptionType() != null ? option.getOptionType().name() : null);
                            optionDto.setExtraPrice(option.getExtraPrice() != null ? option.getExtraPrice() : BigDecimal.ZERO);
                        } else {
                            optionDto.setOptionId(itemOption.getOptionId());
                            optionDto.setExtraPrice(BigDecimal.ZERO);
                        }
                        optionDtos.add(optionDto);
                    }
                }
                itemDto.setOptions(optionDtos);
                itemDtos.add(itemDto);
            }
        }
        dto.setItems(itemDtos);
        return dto;
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
