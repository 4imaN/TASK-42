package com.reclaim.portal.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "reclaim")
public class ReclaimProperties {

    private Security security = new Security();
    private Storage storage = new Storage();
    private Bootstrap bootstrap = new Bootstrap();
    private Appointments appointments = new Appointments();
    private Contracts contracts = new Contracts();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public Appointments getAppointments() {
        return appointments;
    }

    public void setAppointments(Appointments appointments) {
        this.appointments = appointments;
    }

    public Contracts getContracts() {
        return contracts;
    }

    public void setContracts(Contracts contracts) {
        this.contracts = contracts;
    }

    // -------------------------------------------------------------------------
    // Nested: Security
    // -------------------------------------------------------------------------
    public static class Security {

        private String jwtSecret;
        private String refreshSecret;
        private String encryptionKey;
        private int accessTokenMinutes = 30;
        private int refreshTokenDays = 7;
        private int maxFailedAttempts = 5;
        private int lockoutMinutes = 15;
        private boolean cookieSecure = false;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public String getRefreshSecret() {
            return refreshSecret;
        }

        public void setRefreshSecret(String refreshSecret) {
            this.refreshSecret = refreshSecret;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }

        public int getAccessTokenMinutes() {
            return accessTokenMinutes;
        }

        public void setAccessTokenMinutes(int accessTokenMinutes) {
            this.accessTokenMinutes = accessTokenMinutes;
        }

        public int getRefreshTokenDays() {
            return refreshTokenDays;
        }

        public void setRefreshTokenDays(int refreshTokenDays) {
            this.refreshTokenDays = refreshTokenDays;
        }

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getLockoutMinutes() {
            return lockoutMinutes;
        }

        public void setLockoutMinutes(int lockoutMinutes) {
            this.lockoutMinutes = lockoutMinutes;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Storage
    // -------------------------------------------------------------------------
    public static class Storage {

        private String rootPath = "./storage";
        private long maxFileSize = 3145728L;
        private String allowedExtensions = "jpg,jpeg,png";

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public long getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(long maxFileSize) {
            this.maxFileSize = maxFileSize;
        }

        public String getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(String allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }

        public List<String> getAllowedExtensionList() {
            return Arrays.asList(allowedExtensions.split(","));
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Bootstrap
    // -------------------------------------------------------------------------
    public static class Bootstrap {

        private String passwordsFile = "";

        public String getPasswordsFile() {
            return passwordsFile;
        }

        public void setPasswordsFile(String passwordsFile) {
            this.passwordsFile = passwordsFile;
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Appointments
    // -------------------------------------------------------------------------
    public static class Appointments {

        private int businessStartHour = 8;
        private int businessEndHour = 18;
        private int slotMinutes = 30;
        private int minAdvanceHours = 2;
        private int maxAdvanceDays = 14;
        private int pickupCapacity = 5;
        private int dropoffCapacity = 5;

        public int getBusinessStartHour() {
            return businessStartHour;
        }

        public void setBusinessStartHour(int businessStartHour) {
            this.businessStartHour = businessStartHour;
        }

        public int getBusinessEndHour() {
            return businessEndHour;
        }

        public void setBusinessEndHour(int businessEndHour) {
            this.businessEndHour = businessEndHour;
        }

        public int getSlotMinutes() {
            return slotMinutes;
        }

        public void setSlotMinutes(int slotMinutes) {
            this.slotMinutes = slotMinutes;
        }

        public int getMinAdvanceHours() {
            return minAdvanceHours;
        }

        public void setMinAdvanceHours(int minAdvanceHours) {
            this.minAdvanceHours = minAdvanceHours;
        }

        public int getMaxAdvanceDays() {
            return maxAdvanceDays;
        }

        public void setMaxAdvanceDays(int maxAdvanceDays) {
            this.maxAdvanceDays = maxAdvanceDays;
        }

        public int getPickupCapacity() {
            return pickupCapacity;
        }

        public void setPickupCapacity(int pickupCapacity) {
            this.pickupCapacity = pickupCapacity;
        }

        public int getDropoffCapacity() {
            return dropoffCapacity;
        }

        public void setDropoffCapacity(int dropoffCapacity) {
            this.dropoffCapacity = dropoffCapacity;
        }
    }

    // -------------------------------------------------------------------------
    // Nested: Contracts
    // -------------------------------------------------------------------------
    public static class Contracts {

        private int expiringSoonDays = 30;

        public int getExpiringSoonDays() {
            return expiringSoonDays;
        }

        public void setExpiringSoonDays(int expiringSoonDays) {
            this.expiringSoonDays = expiringSoonDays;
        }
    }
}
