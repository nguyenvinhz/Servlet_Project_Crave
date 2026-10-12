package com.foodordering.mapper;

import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.entity.Cart;
import com.foodordering.entity.CartItem;

import java.util.Collections;
import java.util.List;

/**
 * Mapper thủ công chuyển đổi giữa Cart entity và CartDto.
 * Không sử dụng công cụ sinh mã MapStruct, gán dữ liệu qua getter/setter thủ công.
 */
public class CartMapper {

    private final CartItemMapper cartItemMapper;

    public CartMapper() {
        this.cartItemMapper = new CartItemMapper();
    }

    public CartMapper(CartItemMapper cartItemMapper) {
        this.cartItemMapper = cartItemMapper != null ? cartItemMapper : new CartItemMapper();
    }

    /**
     * Chuyển đổi thủ công từ Cart entity sang CartDto (dùng danh sách items có sẵn trong Cart).
     */
    public CartDto toDto(Cart entity) {
        if (entity == null) {
            return null;
        }

        CartDto dto = new CartDto();
        dto.setCartId(entity.getCartId());
        dto.setCustomerId(entity.getCustomer() != null ? entity.getCustomer().getId() : entity.getCustomerId());

        if (entity.getItems() != null) {
            List<CartItemDto> itemDtos = cartItemMapper.toDtoList(entity.getItems());
            dto.setItems(itemDtos);
        } else {
            dto.setItems(Collections.emptyList());
        }

        return dto;
    }

    /**
     * Chuyển đổi thủ công từ Cart entity và danh sách CartItem sang CartDto.
     */
    public CartDto toDto(Cart entity, List<CartItem> items) {
        if (entity == null) {
            return null;
        }

        CartDto dto = new CartDto();
        dto.setCartId(entity.getCartId());
        dto.setCustomerId(entity.getCustomer() != null ? entity.getCustomer().getId() : entity.getCustomerId());

        if (items != null) {
            List<CartItemDto> itemDtos = cartItemMapper.toDtoList(items);
            dto.setItems(itemDtos);
        } else {
            dto.setItems(Collections.emptyList());
        }

        return dto;
    }
}
