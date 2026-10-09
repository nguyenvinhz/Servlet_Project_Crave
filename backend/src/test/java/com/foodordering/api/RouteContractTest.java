package com.foodordering.api;

import com.foodordering.servlet.AccountPageServlet;
import jakarta.servlet.annotation.WebServlet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteContractTest {

    @Test
    void allAccountPageRoutesAreRegistered() {
        assertEquals(Set.of(
                        "/auth/login",
                        "/auth/register",
                        "/customer/profile",
                        "/customer/addresses"),
                patterns(AccountPageServlet.class));
    }

    @Test
    void allAccountApiRootsAreRegistered() {
        assertEquals(Set.of("/api/auth/login", "/api/auth/register"), patterns(AuthApiServlet.class));
        assertEquals(Set.of("/api/profile"), patterns(ProfileApiServlet.class));
        assertEquals(Set.of("/api/addresses", "/api/addresses/*"), patterns(AddressApiServlet.class));
        assertEquals(Set.of("/api/health"), patterns(HealthApiServlet.class));
    }

    @Test
    void cartAndPromotionRoutesAreRegistered() {
        assertEquals(Set.of("/cart", "/cart/"), patterns(com.foodordering.servlet.CartPageServlet.class));
        assertEquals(Set.of("/promotions", "/promotions/"), patterns(com.foodordering.servlet.PromotionPageServlet.class));
        assertEquals(Set.of("/api/cart", "/api/cart/*"), patterns(CartApiServlet.class));
        assertEquals(Set.of("/api/promotions", "/api/promotions/*"), patterns(PromotionApiServlet.class));
    }

    private Set<String> patterns(Class<?> servletType) {
        return Arrays.stream(servletType.getAnnotation(WebServlet.class).urlPatterns()).collect(toSet());
    }
}
