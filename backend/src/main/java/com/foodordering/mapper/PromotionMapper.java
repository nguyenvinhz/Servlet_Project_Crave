package com.foodordering.mapper;

import com.foodordering.dto.PromotionDto;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mapper thủ công chuyển đổi giữa Promotion entity và PromotionDto.
 * Không sử dụng công cụ sinh mã MapStruct, gán dữ liệu qua getter/setter thủ công.
 */
public class PromotionMapper {

    /**
     * Chuyển đổi thủ công từ Promotion entity sang PromotionDto.
     */
    public PromotionDto toDto(Promotion entity) {
        if (entity == null) {
            return null;
        }

        PromotionDto dto = new PromotionDto();
        dto.setPromotionId(entity.getPromotionId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDiscountType(entity.getDiscountType());
        dto.setDiscountValue(entity.getDiscountValue());
        dto.setMinimumOrderValue(entity.getMinimumOrderValue());
        dto.setMaximumDiscount(entity.getMaximumDiscount());
        dto.setStartAt(entity.getStartAt());
        dto.setEndAt(entity.getEndAt());
        dto.setStatus(entity.getStatus());

        if (entity.getDiscountType() == DiscountType.PERCENT) {
            String maxDesc = (entity.getMaximumDiscount() != null && entity.getMaximumDiscount().doubleValue() > 0)
                    ? String.format(", tối đa %,.0f đ", entity.getMaximumDiscount().doubleValue())
                    : "";
            dto.setDescription(String.format("Giảm %s%% cho đơn từ %,.0f đ%s",
                    entity.getDiscountValue() != null ? entity.getDiscountValue().toPlainString() : "0",
                    entity.getMinimumOrderValue() != null ? entity.getMinimumOrderValue().doubleValue() : 0,
                    maxDesc));
        } else {
            dto.setDescription(String.format("Giảm %,.0f đ cho đơn từ %,.0f đ",
                    entity.getDiscountValue() != null ? entity.getDiscountValue().doubleValue() : 0,
                    entity.getMinimumOrderValue() != null ? entity.getMinimumOrderValue().doubleValue() : 0));
        }

        return dto;
    }

    /**
     * Chuyển đổi danh sách Promotion entity sang danh sách PromotionDto.
     */
    public List<PromotionDto> toDtoList(List<Promotion> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<PromotionDto> dtos = new ArrayList<>();
        for (Promotion entity : entities) {
            PromotionDto dto = toDto(entity);
            if (dto != null) {
                dtos.add(dto);
            }
        }
        return dtos;
    }
}
