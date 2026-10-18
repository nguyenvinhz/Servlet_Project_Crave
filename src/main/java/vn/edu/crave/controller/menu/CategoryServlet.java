package vn.edu.crave.controller.menu;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "CategoryServlet", urlPatterns = {"/categories", "/categories/*"})
public class CategoryServlet extends HttpServlet {
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = request.getPathInfo();
        String destination = "/menu";
        if (id != null && id.length() > 1) {
            destination += "?categoryId=" + URLEncoder.encode(id.substring(1), StandardCharsets.UTF_8);
        }
        response.sendRedirect(request.getContextPath() + destination);
    }
}