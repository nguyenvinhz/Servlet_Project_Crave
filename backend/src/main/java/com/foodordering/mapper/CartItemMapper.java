package com.foodordering.mapper;

import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.CartItemOptionDto;
import com.foodordering.entity.CartItem;
import com.foodordering.entity.Food;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mapper thủ công chuyển đổi giữa CartItem entity và CartItemDto.
 * Không sử dụng công cụ sinh mã MapStruct, gán dữ liệu qua getter/setter thủ công.
 */
public class CartItemMapper {

    private final CartItemOptionMapper optionMapper;

    public CartItemMapper() {
        this.optionMapper = new CartItemOptionMapper();
    }

    public CartItemMapper(CartItemOptionMapper optionMapper) {
        this.optionMapper = optionMapper != null ? optionMapper : new CartItemOptionMapper();
    }

    /**
     * Chuyển đổi thủ công từ CartItem entity sang CartItemDto.
     */
    public CartItemDto toDto(CartItem entity) {
        if (entity == null) {
            return null;
        }

        CartItemDto dto = new CartItemDto();
        dto.setCartItemId(entity.getCartItemId());
        dto.setCartId(entity.getCart() != null ? entity.getCart().getCartId() : entity.getCartId());
        dto.setQuantity(entity.getQuantity());
        dto.setNote(entity.getNote());

        Food food = entity.getFood();
        if (food != null) {
            dto.setFoodId(food.getFoodId());
            dto.setFoodName(food.getName());
            dto.setImageUrl(food.getImageUrl());
            dto.setBasePrice(food.getBasePrice() != null ? food.getBasePrice() : BigDecimal.ZERO);
        } else {
            dto.setFoodId(entity.getFoodId());
            dto.setBasePrice(BigDecimal.ZERO);
        }

        List<CartItemOptionDto> options = optionMapper.toDtoList(entity.getItemOptions());
        dto.setOptions(options);

        return dto;
    }

    /**
     * Chuyển đổi danh sách CartItem entity sang danh sách CartItemDto.
     */
    public List<CartItemDto> toDtoList(List<CartItem> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<CartItemDto> dtos = new ArrayList<>();
        for (CartItem entity : entities) {
            CartItemDto dto = toDto(entity);
            if (dto != null) {
                dtos.add(dto);
            }
        }
        return dtos;
    }
}
