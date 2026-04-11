package com.reclaim.portal.users.service;

import com.reclaim.portal.auth.entity.AdminAccessLog;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.AdminAccessLogRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.users.dto.MaskedUserProfileDto;
import com.reclaim.portal.users.dto.UserProfileDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AdminAccessLogRepository adminAccessLogRepository;

    public UserService(UserRepository userRepository,
                       AdminAccessLogRepository adminAccessLogRepository) {
        this.userRepository = userRepository;
        this.adminAccessLogRepository = adminAccessLogRepository;
    }

    /**
     * Returns the full (unmasked) profile for the given user id.
     */
    public UserProfileDto getUserProfile(Long userId) {
        User user = findUserById(userId);
        return toProfileDto(user);
    }

    /**
     * Returns a PII-masked profile for the given user id.
     */
    public MaskedUserProfileDto getMaskedUserProfile(Long userId) {
        User user = findUserById(userId);
        return MaskedUserProfileDto.of(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getCreatedAt()
        );
    }

    /**
     * Reveals PII for a target user, logging the access in the AdminAccessLog.
     *
     * @param adminUserId  the id of the admin performing the reveal
     * @param targetUserId the id of the user whose PII is being revealed
     * @param reason       the stated reason for the reveal
     * @return the full unmasked profile
     */
    public UserProfileDto revealPii(Long adminUserId, Long targetUserId, String reason) {
        User target = findUserById(targetUserId);

        AdminAccessLog log = new AdminAccessLog();
        log.setAdminUserId(adminUserId);
        log.setActionType("PII_REVEAL");
        log.setTargetEntity("User");
        log.setTargetId(targetUserId);
        log.setFieldsRevealed("fullName,email,phone");
        log.setReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        adminAccessLogRepository.save(log);

        return toProfileDto(target);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User", id));
    }

    private UserProfileDto toProfileDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getSellerCreditScore(),
                user.getRoles().stream()
                        .map(r -> r.getName())
                        .collect(Collectors.toSet()),
                user.getCreatedAt()
        );
    }
}
