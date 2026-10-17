package vn.edu.crave.controller;

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

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteContractTest {

    @Test
    void allAccountPageRoutesAreRegistered() {
        assertEquals(Set.of(
                        "/auth/login",
                        "/auth/register",
                        "/auth/forgot-password", "/auth/change-password",
                        "/customer/profile",
                        "/customer/addresses"),
                patterns(AccountPageServlet.class));
    }

    @Test
    void allAccountFormActionsAreRegistered() {
        assertEquals(Set.of("/auth/login/submit", "/auth/register/submit", "/auth/logout"),
                patterns(AuthServlet.class));
        assertEquals(Set.of("/auth/register/code", "/auth/password/reset/code",
                "/auth/password/reset/submit", "/auth/password/change/code", "/auth/password/change/submit"),
                patterns(EmailVerificationServlet.class));
        assertEquals(Set.of("/customer/profile/save"), patterns(ProfileServlet.class));
        assertEquals(Set.of("/customer/addresses/save", "/customer/addresses/delete", "/customer/addresses/default"), patterns(AddressServlet.class));
    }

    @Test
    void cartAndPromotionRoutesAreRegistered() {
        assertEquals(Set.of("/cart", "/cart/"), patterns(vn.edu.crave.controller.cart.CartPageServlet.class));
        assertEquals(Set.of("/promotions", "/promotions/"), patterns(vn.edu.crave.controller.cart.PromotionPageServlet.class));
        assertEquals(Set.of("/cart/action"), patterns(CartServlet.class));
        assertEquals(Set.of("/promotions/apply", "/promotions/remove"), patterns(PromotionServlet.class));
    }

    @Test
    void orderAndPayosRoutesUseNativePagesFormsAndOneServerWebhook() {
        assertEquals(Set.of("/checkout"), patterns(CheckoutPageServlet.class));
        assertEquals(Set.of("/orders/*"), patterns(OrdersPageServlet.class));
        assertEquals(Set.of("/orders/create", "/orders/cancel"), patterns(OrderServlet.class));
        assertEquals(Set.of("/admin/orders"), patterns(AdminOrdersPageServlet.class));
        assertEquals(Set.of("/admin/orders/action"), patterns(AdminOrderServlet.class));
        assertEquals(Set.of("/admin/orders/payment"), patterns(PaymentServlet.class));
        assertEquals(Set.of("/payos/create", "/payos/status", "/payos/qr"), patterns(PayosPaymentServlet.class));
        assertEquals(Set.of("/api/payos/webhook"), patterns(PayosWebhookServlet.class));
    }

    private Set<String> patterns(Class<?> servletType) {
        return Arrays.stream(servletType.getAnnotation(WebServlet.class).urlPatterns()).collect(toSet());
    }
}
