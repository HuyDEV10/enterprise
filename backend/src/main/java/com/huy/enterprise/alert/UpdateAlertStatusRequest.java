package com.huy.enterprise.alert;
import com.huy.enterprise.common.enums.AlertStatus; import jakarta.validation.constraints.NotNull;
public record UpdateAlertStatusRequest(@NotNull AlertStatus status){}
