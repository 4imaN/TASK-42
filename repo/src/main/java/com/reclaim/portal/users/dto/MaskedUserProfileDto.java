package com.reclaim.portal.users.dto;

import java.time.LocalDateTime;

public class MaskedUserProfileDto {

    private Long id;
    private String username;
    private String maskedFullName;
    private String maskedEmail;
    private String maskedPhone;
    private LocalDateTime createdAt;

    public MaskedUserProfileDto() {
    }

    public MaskedUserProfileDto(Long id, String username, String maskedFullName,
                                String maskedEmail, String maskedPhone, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.maskedFullName = maskedFullName;
        this.maskedEmail = maskedEmail;
        this.maskedPhone = maskedPhone;
        this.createdAt = createdAt;
    }

    /**
     * Creates a MaskedUserProfileDto from raw PII fields, applying masking to each.
     */
    public static MaskedUserProfileDto of(Long id, String username, String fullName,
                                          String email, String phone, LocalDateTime createdAt) {
        return new MaskedUserProfileDto(
                id,
                username,
                maskFullName(fullName),
                maskEmail(email),
                maskPhone(phone),
                createdAt
        );
    }

    /**
     * Masks a full name.
     * null/empty → "***"
     * otherwise → firstChar + "***"
     */
    public static String maskFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "***";
        }
        return fullName.charAt(0) + "***";
    }

    /**
     * Masks an email address.
     * null → "***"
     * no '@' → "***"
     * otherwise → first2chars + "***@" + domain
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        if (atIdx < 0) {
            return "***";
        }
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);
        String prefix = local.length() >= 2 ? local.substring(0, 2) : local;
        return prefix + "***@" + domain;
    }

    /**
     * Masks a phone number.
     * null or <= 4 chars → "***"
     * otherwise → "***" + last4
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMaskedFullName() {
        return maskedFullName;
    }

    public void setMaskedFullName(String maskedFullName) {
        this.maskedFullName = maskedFullName;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public void setMaskedEmail(String maskedEmail) {
        this.maskedEmail = maskedEmail;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
