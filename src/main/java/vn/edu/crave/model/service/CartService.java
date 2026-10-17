package vn.edu.crave.model.service;

import vn.edu.crave.model.form.AddToCartRequest;
import vn.edu.crave.model.viewmodel.CartDto;
import vn.edu.crave.model.viewmodel.CartItemDto;
import vn.edu.crave.model.viewmodel.CartItemOptionDto;
import vn.edu.crave.model.form.UpdateCartItemRequest;
import vn.edu.crave.model.entity.Cart;
import vn.edu.crave.model.entity.CartItem;
import vn.edu.crave.model.entity.CartItemOption;
import vn.edu.crave.model.entity.Food;
import vn.edu.crave.model.entity.FoodOption;
import vn.edu.crave.model.enums.ErrorCode;
import vn.edu.crave.exception.BadRequestException;
import vn.edu.crave.exception.ResourceNotFoundException;
import vn.edu.crave.model.dao.CartItemDAO;
import vn.edu.crave.model.dao.CartDAO;
import vn.edu.crave.model.validator.CartValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CartService {

    private final CartDAO cartDAO;
    private final CartItemDAO cartItemDAO;

    public CartService() {
        this.cartDAO = new CartDAO();
        this.cartItemDAO = new CartItemDAO();
    }

    public CartService(CartDAO cartDAO, CartItemDAO cartItemDAO) {
        this.cartDAO = cartDAO;
        this.cartItemDAO = cartItemDAO;
    }

    /**
     * Lấy hoặc tạo giỏ hàng và chuẩn bị dữ liệu hiển thị cho khách hàng.
     */
    public CartDto getOrCreateCart(String customerId) {
        CartValidator.validateCustomerId(customerId);
        Cart cart = cartDAO.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartDAO.createCart(customerId);
        }
        List<CartItem> items = cartItemDAO.findItemsByCartId(cart.getCartId());
        CartDto cartDto = createCartDto(cart, items);
        calculateCartTotals(cartDto);
        return cartDto;
    }

    public CartDto addItem(String customerId, AddToCartRequest request) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateAddToCart(request);

        boolean foodAvailable = cartItemDAO.isFoodAvailable(request.getFoodId());
        if (!foodAvailable) {
            throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món ăn hiện không khả dụng hoặc đã ngừng phục vụ");
        }
        List<String> rawOptions = request.getOptionIds() != null ? request.getOptionIds() : Collections.emptyList();
        if (!rawOptions.isEmpty()) {
            List<String> validOptions = cartItemDAO.getValidOptionIdsForFood(request.getFoodId(), rawOptions);
            if (validOptions.size() != rawOptions.size()) {
                throw new BadRequestException(ErrorCode.INVALID_OPTION, "Một số tùy chọn món không hợp lệ hoặc đã ngừng áp dụng");
            }
        }

        // 3. Lấy hoặc tạo giỏ hàng
        Cart cart = cartDAO.findByCustomerId(customerId);
        if (cart == null) {
            cart = cartDAO.createCart(customerId);
        }

        // 4. Kiểm tra món trùng (cùng foodId và cùng tập hợp tùy chọn)
        CartItem duplicateItem = cartItemDAO.findDuplicateItem(cart.getCartId(), request.getFoodId(), rawOptions);
        if (duplicateItem != null) {
            int newQty = duplicateItem.getQuantity() + request.getQuantity();
            if (newQty > 100) {
                throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Tổng số lượng cho món này trong giỏ vượt quá giới hạn 100 phần");
            }
            cartItemDAO.updateQuantity(duplicateItem.getCartItemId(), newQty);
            if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
                cartItemDAO.updateNote(duplicateItem.getCartItemId(), request.getNote().trim());
            }
        } else {
            CartItem newItem = new CartItem();
            newItem.setCartId(cart.getCartId());
            newItem.setFoodId(request.getFoodId());
            newItem.setQuantity(request.getQuantity());
            newItem.setNote(request.getNote() != null ? request.getNote().trim() : null);
            cartItemDAO.insertItemWithOptions(newItem, rawOptions);
        }

        return getOrCreateCart(customerId);
    }

    public CartDto updateItem(String customerId, UpdateCartItemRequest request) {
        CartValidator.validateCustomerId(customerId);
        CartValidator.validateUpdateCartItem(request);

        Cart cart = cartDAO.findByCustomerId(customerId);
        if (cart == null) {
            throw new ResourceNotFoundException(ErrorCode.CART_NOT_FOUND, "Không tìm thấy giỏ hàng của bạn");
        }
        CartItem item = cartItemDAO.findById(request.getCartItemId());
        if (item == null || !cart.getCartId().equals(item.getCartId())) {
            throw new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND, "Món không tồn tại trong giỏ hàng của bạn");
        }

        if (request.getQuantity() == 0) {
            cartItemDAO.deleteItem(item.getCartItemId());
        } else if (request.getOptionIds() != null) {
            if (!cartItemDAO.isFoodAvailable(item.getFoodId())) {
                throw new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món ăn hiện không khả dụng hoặc đã ngừng phục vụ");
            }
            List<String> validOptions = cartItemDAO.getValidOptionIdsForFood(item.getFoodId(), request.getOptionIds());
            if (validOptions.size() != request.getOptionIds().size()) {
                throw new BadRequestException(ErrorCode.INVALID_OPTION, "Một số tùy chọn món không hợp lệ hoặc đã ngừng áp dụng");
            }
            cartItemDAO.updateItemWithOptions(item.getCartItemId(), cart.getCartId(), request.getQuantity(),
                    request.getNote() == null ? null : request.getNote().trim(), request.getOptionIds());
        } else {
            cartItemDAO.updateQuantity(item.getCartItemId(), request.getQuantity());
            if (request.getNote() != null) {
                cartItemDAO.updateNote(item.getCartItemId(), request.getNote().trim());
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

        Cart cart = cartDAO.findByCustomerId(customerId);
        if (cart == null) {
            throw new ResourceNotFoundException(ErrorCode.CART_NOT_FOUND, "Không tìm thấy giỏ hàng của bạn");
        }

        CartItem item = cartItemDAO.findById(cartItemId);
        if (item == null || !cart.getCartId().equals(item.getCartId())) {
            throw new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND, "Món không tồn tại trong giỏ hàng của bạn");
        }

        cartItemDAO.deleteItem(cartItemId);
        return getOrCreateCart(customerId);
    }

    /**
     * Xóa toàn bộ món trong giỏ hàng.
     */
    public CartDto clearCart(String customerId) {
        CartValidator.validateCustomerId(customerId);
        Cart cart = cartDAO.findByCustomerId(customerId);
        if (cart != null) {
            cartItemDAO.clearCart(cart.getCartId());
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
