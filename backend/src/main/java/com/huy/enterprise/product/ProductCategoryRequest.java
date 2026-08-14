package com.huy.enterprise.product;

import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.validation.constraints.NotBlank;

public record ProductCategoryRequest(@NotBlank String name, @NotBlank String code, String description, RecordStatus status) {}
