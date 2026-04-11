package com.reclaim.portal.users.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public class UserProfileDto {

    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private BigDecimal sellerCreditScore;
    private Set<String> roles;
    private LocalDateTime createdAt;

    public UserProfileDto() {
    }

    public UserProfileDto(Long id, String username, String fullName, String email, String phone,
                          BigDecimal sellerCreditScore, Set<String> roles, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.sellerCreditScore = sellerCreditScore;
        this.roles = roles;
        this.createdAt = createdAt;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public BigDecimal getSellerCreditScore() {
        return sellerCreditScore;
    }

    public void setSellerCreditScore(BigDecimal sellerCreditScore) {
        this.sellerCreditScore = sellerCreditScore;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
