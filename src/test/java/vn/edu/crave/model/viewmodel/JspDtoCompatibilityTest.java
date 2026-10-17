package vn.edu.crave.model.viewmodel;

import vn.edu.crave.model.enums.FoodStatus;
import vn.edu.crave.model.enums.OptionStatus;
import vn.edu.crave.model.enums.OptionType;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JspDtoCompatibilityTest {
    @Test
    void menuRecordsExposePropertiesUsedByServlet6Jsp() throws Exception {
        var category = new CategoryResponse("DM01", "Món chính", "Cơm và mì", 1);
        var option = new FoodOptionResponse("TC01", "MA01", OptionType.SIZE, "Lớn", BigDecimal.TEN, OptionStatus.ACTIVE);
        var food = new FoodDetailResponse("MA01", "DM01", category.name(), "Cơm", BigDecimal.valueOf(45000),
                "", "Cơm nóng", FoodStatus.AVAILABLE, List.of(option));
        var summary = new FoodSummaryResponse(food.id(), food.categoryId(), food.categoryName(), food.name(),
                food.price(), food.imageUrl(), food.description(), food.status());

        assertEquals(category.id(), property(category, "id"));
        assertEquals(category.name(), property(category, "name"));
        assertEquals(category.foodCount(), property(category, "foodCount"));
        assertEquals(summary.price(), property(summary, "price"));
        assertEquals(summary.status(), property(summary, "status"));
        assertEquals(food.options(), property(food, "options"));
        assertEquals(option.optionType(), property(option, "optionType"));
        assertEquals(option.extraPrice(), property(option, "extraPrice"));

    }

    private Object property(Object bean, String name) throws Exception {
        for (var descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if (descriptor.getName().equals(name) && descriptor.getReadMethod() != null) {
                return descriptor.getReadMethod().invoke(bean);
            }
        }
        throw new AssertionError("Missing JSP property " + name + " on " + bean.getClass().getSimpleName());
    }
}
