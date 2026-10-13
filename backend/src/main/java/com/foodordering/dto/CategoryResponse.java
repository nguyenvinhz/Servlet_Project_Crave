package com.foodordering.dto;

import java.util.List;

public record CategoryResponse(
        String id,
        String name,
        String description,
        long foodCount,
        List<String> imageUrls
) {
    public CategoryResponse(String id, String name, String description, long foodCount) {
        this(id, name, description, foodCount, List.of());
    }

    // Servlet 6 / JSP EL reads JavaBean accessors rather than record components.
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getFoodCount() { return foodCount; }
    public List<String> getImageUrls() { return imageUrls != null ? imageUrls : List.of(); }
}
