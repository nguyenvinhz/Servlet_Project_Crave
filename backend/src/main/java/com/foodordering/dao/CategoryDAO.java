package com.foodordering.dao;

import com.foodordering.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryDAO {

    List<Category> findAll();

    Optional<Category> findById(String id);

    Optional<Category> findByName(String name);

    boolean existsById(String id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    Category save(Category category);

    Category update(Category category);

    void deleteById(String id);

    String generateNextId();
}
