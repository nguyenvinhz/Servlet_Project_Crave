package com.foodordering.dto;

public record CategoryResponse(
        String id,
        String name,
        String description,
        long foodCount
) {
    // Servlet 6 / JSP EL reads JavaBean accessors rather than record components.
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getFoodCount() { return foodCount; }
}
