package com.huy.enterprise.audit;

import com.huy.enterprise.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository logs;
    private final UserRepository users;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, String description) {
        AuditLog log = new AuditLog();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            users.findByUsername(authentication.getName()).ifPresent(log::setActorUser);
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        logs.save(log);
    }
}
