package com.foodordering.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "addressApiServlet", urlPatterns = {"/api/addresses", "/api/addresses/*"})
public class AddressApiServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String addressId = addressId(request);
        notImplemented(response, addressId == null
                ? "List current customer's delivery addresses"
                : "Get delivery address " + addressId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (addressId(request) != null) {
            badRequest(response, "POST is only valid on /api/addresses.");
            return;
        }
        notImplemented(response, "Create delivery address");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String addressId = addressId(request);
        if (addressId == null) {
            badRequest(response, "An addressId path parameter is required.");
            return;
        }
        notImplemented(response, "Update delivery address " + addressId);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String addressId = addressId(request);
        if (addressId == null) {
            badRequest(response, "An addressId path parameter is required.");
            return;
        }
        notImplemented(response, "Delete delivery address " + addressId);
    }

    private String addressId(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            return null;
        }
        String value = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        return value.isBlank() ? null : value;
    }
}
