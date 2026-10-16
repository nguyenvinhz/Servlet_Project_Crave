package com.foodordering.bean;

import com.foodordering.model.FoodOption;
import java.math.BigDecimal;

/**
 * bean chứa thông tin snapshot tùy chọn của món ăn trong giỏ hàng.
 */
public class CartItemOptionBean {
    public CartItemOptionBean(com.foodordering.model.CartItemOption entity) {

        FoodOption foodOption = entity.getOption();
        if (foodOption != null) {
            this.setOptionId(foodOption.getOptionId());
            this.setName(foodOption.getName());
            this.setOptionType(foodOption.getOptionType() != null ? foodOption.getOptionType().name() : null);
            this.setExtraPrice(foodOption.getExtraPrice() != null ? foodOption.getExtraPrice() : BigDecimal.ZERO);
        } else {
            this.setOptionId(entity.getOptionId());
            this.setExtraPrice(BigDecimal.ZERO);
        }

    }

    private String optionId;
    private String name;
    private String optionType;
    private BigDecimal extraPrice;

    public CartItemOptionBean() {
        this.extraPrice = BigDecimal.ZERO;
    }

    public CartItemOptionBean(String optionId, String name, String optionType, BigDecimal extraPrice) {
        this.optionId = optionId;
        this.name = name;
        this.optionType = optionType;
        this.extraPrice = extraPrice != null ? extraPrice : BigDecimal.ZERO;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOptionType() {
        return optionType;
    }

    public void setOptionType(String optionType) {
        this.optionType = optionType;
    }

    public BigDecimal getExtraPrice() {
        return extraPrice;
    }

    public void setExtraPrice(BigDecimal extraPrice) {
        this.extraPrice = extraPrice;
    }
}
