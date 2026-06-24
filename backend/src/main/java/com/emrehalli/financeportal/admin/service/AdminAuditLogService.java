package com.emrehalli.financeportal.admin.service;

import com.emrehalli.financeportal.admin.dto.AdminAuditLogResponseDto;
import com.emrehalli.financeportal.admin.entity.AdminAuditLog;
import com.emrehalli.financeportal.admin.enums.AdminAuditAction;
import com.emrehalli.financeportal.admin.repository.AdminAuditLogRepository;
import com.emrehalli.financeportal.common.logging.StructuredLogContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditLogService {

    private static final Logger logger = LogManager.getLogger(AdminAuditLogService.class);

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Transactional
    public void log(Long actorUserId, Long targetUserId, AdminAuditAction action, String description, String metadata) {
        AdminAuditLog saved = adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .action(action)
                .description(normalize(description))
                .metadata(normalize(metadata))
                .build());

        try (StructuredLogContext ignored = StructuredLogContext.open()
                .put("auditLogId", saved.getId())
                .put("actorUserId", actorUserId)
                .put("targetUserId", targetUserId)
                .put("auditAction", action)
                .put("success", true)) {
            logger.info("Admin audit action recorded");
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponseDto> getAuditLogs(
            AdminAuditAction action,
            Long targetUserId,
            Long actorUserId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Specification<AdminAuditLog> specification = Specification.where(null);

        if (action != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (targetUserId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("targetUserId"), targetUserId));
        }
        if (actorUserId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }

        return adminAuditLogRepository.findAll(specification, pageable).map(this::toResponse);
    }

    private AdminAuditLogResponseDto toResponse(AdminAuditLog log) {
        return AdminAuditLogResponseDto.builder()
                .id(log.getId())
                .actorUserId(log.getActorUserId())
                .targetUserId(log.getTargetUserId())
                .action(log.getAction())
                .description(log.getDescription())
                .metadata(log.getMetadata())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

