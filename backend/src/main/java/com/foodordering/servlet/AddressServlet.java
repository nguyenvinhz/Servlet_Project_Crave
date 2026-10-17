package com.foodordering.servlet;

import com.foodordering.dto.AddressResponse;
import com.foodordering.dto.AddressWriteRequest;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "addressServlet", urlPatterns = {
        "/customer/addresses/save", "/customer/addresses/delete", "/customer/addresses/default"
})
public class AddressServlet extends AccountBaseServlet {
    private final AccountService accounts;
    public AddressServlet() { this(new AccountService()); }
    public AddressServlet(AccountService accounts) { this.accounts = accounts; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String customerId = null;
        try {
            customerId = SessionAuth.requireCustomer(request).id();
            String id = request.getParameter("addressId");
            switch (request.getServletPath()) {
                case "/customer/addresses/delete" -> accounts.deleteAddress(customerId, id);
                case "/customer/addresses/default" -> {
                    AddressResponse address = accounts.getAddress(customerId, id);
                    accounts.updateAddress(customerId, id,
                            new AddressWriteRequest(address.addressLine(), address.note(), true));
                }
                default -> {
                    AddressWriteRequest address = new AddressWriteRequest(request.getParameter("addressLine"),
                            request.getParameter("note"), "true".equals(request.getParameter("isDefault")));
                    if (id == null || id.isBlank()) accounts.createAddress(customerId, address);
                    else accounts.updateAddress(customerId, id, address);
                }
            }
            response.sendRedirect(request.getContextPath() + "/customer/addresses?saved=1");
        } catch (Exception exception) {
            if (customerId != null) {
                try { request.setAttribute("addresses", accounts.listAddresses(customerId)); }
                catch (RuntimeException ignored) { /* Display the original form error. */ }
            }
            request.setAttribute("editing", request.getParameter("addressId"));
            formError(request, response, "customer/addresses", exception);
        }
    }
}