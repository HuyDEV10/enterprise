package com.huy.enterprise.alert;
import java.util.UUID; import com.huy.enterprise.common.enums.AlertPriority; import jakarta.validation.constraints.*;
public record CreateAlertRequest(@NotBlank String title,@NotBlank String message,@NotBlank String alertType,AlertPriority priority,UUID recipientUserId,UUID riskEventId){}
