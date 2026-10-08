package com.foodordering.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "authApiServlet", urlPatterns = {"/api/auth/login", "/api/auth/register"})
public class AuthApiServlet extends BaseApiServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String operation = request.getServletPath().endsWith("/login")
                ? "Login"
                : "Customer registration";
        notImplemented(response, operation);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        methodNotAllowed(response, "POST");
    }
}
