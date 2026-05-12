package com.emrehalli.financeportal.audit.service;

import com.emrehalli.financeportal.audit.dto.AdminAuditLogResponseDto;
import com.emrehalli.financeportal.audit.entity.AdminAuditLog;
import com.emrehalli.financeportal.audit.enums.AdminAuditAction;
import com.emrehalli.financeportal.audit.repository.AdminAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    public AdminAuditLogService(AdminAuditLogRepository adminAuditLogRepository) {
        this.adminAuditLogRepository = adminAuditLogRepository;
    }

    @Transactional
    public void log(Long actorUserId, Long targetUserId, AdminAuditAction action, String description, String metadata) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .actorUserId(actorUserId)
                .targetUserId(targetUserId)
                .action(action)
                .description(normalize(description))
                .metadata(normalize(metadata))
                .build());
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
