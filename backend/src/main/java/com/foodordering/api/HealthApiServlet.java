package com.foodordering.api;

import com.foodordering.config.DatabaseContextListener;
import com.foodordering.dto.ApiError;
import com.foodordering.dto.ApiResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(name = "healthApiServlet", urlPatterns = "/api/health")
public class HealthApiServlet extends BaseApiServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String databaseStatus = (String) getServletContext()
                .getAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE);
        boolean databaseUp = "UP".equals(databaseStatus);

        if (!databaseUp) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ApiResponse.failure(new ApiError(
                            "DATABASE_UNAVAILABLE",
                            "The application is running but the database connection is unavailable.",
                            Map.of())));
            return;
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("application", "UP");
        data.put("database", "UP");
        writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(data));
    }
}
