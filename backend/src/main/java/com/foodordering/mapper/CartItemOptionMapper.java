package com.foodordering.mapper;

import com.foodordering.dto.CartItemOptionDto;
import com.foodordering.entity.CartItemOption;
import com.foodordering.entity.FoodOption;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mapper thủ công chuyển đổi giữa CartItemOption entity và CartItemOptionDto.
 * Không sử dụng công cụ sinh mã MapStruct, gán dữ liệu qua getter/setter thủ công.
 */
public class CartItemOptionMapper {

    /**
     * Chuyển đổi thủ công từ CartItemOption entity sang CartItemOptionDto.
     */
    public CartItemOptionDto toDto(CartItemOption entity) {
        if (entity == null) {
            return null;
        }

        CartItemOptionDto dto = new CartItemOptionDto();
        FoodOption foodOption = entity.getOption();
        if (foodOption != null) {
            dto.setOptionId(foodOption.getOptionId());
            dto.setName(foodOption.getName());
            dto.setOptionType(foodOption.getOptionType() != null ? foodOption.getOptionType().name() : null);
            dto.setExtraPrice(foodOption.getExtraPrice() != null ? foodOption.getExtraPrice() : BigDecimal.ZERO);
        } else {
            dto.setOptionId(entity.getOptionId());
            dto.setExtraPrice(BigDecimal.ZERO);
        }

        return dto;
    }

    /**
     * Chuyển đổi danh sách CartItemOption entity sang danh sách CartItemOptionDto.
     */
    public List<CartItemOptionDto> toDtoList(List<CartItemOption> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<CartItemOptionDto> dtos = new ArrayList<>();
        for (CartItemOption entity : entities) {
            CartItemOptionDto dto = toDto(entity);
            if (dto != null) {
                dtos.add(dto);
            }
        }
        return dtos;
    }
}
