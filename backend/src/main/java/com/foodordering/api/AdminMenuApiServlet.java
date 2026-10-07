package com.foodordering.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Endpoint quản trị menu (Ngày 1: Thiết lập route nền, trả về HTTP 501 để chờ Ngày 2).
 */
@WebServlet(name = "adminMenuApiServlet", urlPatterns = {
        "/api/admin/categories",
        "/api/admin/categories/*",
        "/api/admin/foods",
        "/api/admin/foods/*",
        "/api/admin/options/*"
})
public class AdminMenuApiServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Admin menu query");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Admin create menu resource");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Admin update menu resource");
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Admin delete menu resource");
    }
}
