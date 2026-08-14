package com.huy.enterprise.user;
import com.huy.enterprise.common.enums.RecordStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateUserStatusRequest(@NotNull RecordStatus status) {}
