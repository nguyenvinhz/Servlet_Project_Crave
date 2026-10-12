package com.foodordering.api;

import com.foodordering.servlet.MenuPageServlet;
import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuRouteContractTest {

    @Test
    void allMenuPageRoutesAreRegistered() {
        assertEquals(Set.of(
                        "/menu",
                        "/menu/detail",
                        "/admin/menu",
                        "/admin/categories"),
                patterns(MenuPageServlet.class));
    }

    @Test
    void allMenuApiRootsAreRegistered() {
        assertEquals(Set.of("/api/categories", "/api/categories/*"), patterns(CategoryApiServlet.class));
        assertEquals(Set.of("/api/foods", "/api/foods/*"), patterns(FoodApiServlet.class));
        assertEquals(Set.of(
                        "/api/admin/categories",
                        "/api/admin/categories/*",
                        "/api/admin/foods",
                        "/api/admin/foods/*",
                        "/api/admin/options",
                        "/api/admin/options/*"),
                patterns(AdminMenuApiServlet.class));
    }

    private Set<String> patterns(Class<?> servletType) {
        return Arrays.stream(servletType.getAnnotation(WebServlet.class).urlPatterns()).collect(toSet());
    }
}
