package com.foodordering.api;

import com.foodordering.dto.AddressWriteRequest;
import com.foodordering.dto.ApiResponse;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "addressApiServlet", urlPatterns = {"/api/addresses", "/api/addresses/*"})
public class AddressApiServlet extends AccountApiServlet {
    private final AccountService accountService;

    public AddressApiServlet() {
        this(new AccountService());
    }

    public AddressApiServlet(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected String allowedMethods() {
        return "GET, POST, PUT, DELETE";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String id = addressId(request);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(id == null
                    ? accountService.listAddresses(customerId) : accountService.getAddress(customerId, id)));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            if (addressId(request) != null) {
                throw new AccountException("INVALID_REQUEST", "Chỉ tạo địa chỉ tại /api/addresses.", 400);
            }
            writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(
                    accountService.createAddress(customerId, readAccountJson(request, AddressWriteRequest.class))));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String id = requiredAddressId(request);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(
                    accountService.updateAddress(customerId, id, readAccountJson(request, AddressWriteRequest.class))));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            accountService.deleteAddress(customerId, requiredAddressId(request));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    private String requiredAddressId(HttpServletRequest request) {
        String id = addressId(request);
        if (id == null) {
            throw new AccountException("INVALID_REQUEST", "Vui lòng cung cấp mã địa chỉ.", 400);
        }
        return id;
    }

    private String addressId(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            return null;
        }
        String value = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (!value.matches("[A-Za-z0-9_-]{1,10}")) {
            throw new AccountException("INVALID_REQUEST", "Mã địa chỉ không hợp lệ.", 400);
        }
        return value;
    }
}
