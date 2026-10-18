package vn.edu.crave.controller.menu;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuRouteContractTest {
    @Test
    void publicMenuPagesAndFormActionsHaveSeparateServletMappings() {
        assertEquals(Set.of("/menu", "/menu/detail"), patterns(MenuPageServlet.class));
        assertEquals(Set.of("/cart/action"), patterns(CartServlet.class));
        assertEquals(Set.of("/promotions/apply", "/promotions/remove"), patterns(PromotionServlet.class));
    }

    private Set<String> patterns(Class<?> servlet) {
        return Arrays.stream(servlet.getAnnotation(WebServlet.class).urlPatterns()).collect(Collectors.toSet());
    }
}