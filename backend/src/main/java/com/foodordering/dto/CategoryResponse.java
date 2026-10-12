package com.foodordering.dto;

public record CategoryResponse(
        String id,
        String name,
        String description,
        long foodCount
) {
    // Tomcat 10.1 JSP EL resolves JavaBean getters rather than record accessors.
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getFoodCount() { return foodCount; }
}
