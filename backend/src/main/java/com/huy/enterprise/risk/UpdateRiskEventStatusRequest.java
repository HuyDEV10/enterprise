package com.huy.enterprise.risk;
import com.huy.enterprise.common.enums.RiskEventStatus; import jakarta.validation.constraints.NotNull;
public record UpdateRiskEventStatusRequest(@NotNull RiskEventStatus status){}
