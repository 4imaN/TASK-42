package com.reclaim.portal.unit;

import com.reclaim.portal.users.dto.MaskedUserProfileDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MaskedUserProfileDtoTest {

    @Test
    void shouldMaskEmail() {
        String masked = MaskedUserProfileDto.maskEmail("johndoe@example.com");

        assertThat(masked).isEqualTo("jo***@example.com");
    }

    @Test
    void shouldMaskEmailWithShortLocalPart() {
        String masked = MaskedUserProfileDto.maskEmail("a@example.com");

        assertThat(masked).isEqualTo("a***@example.com");
    }

    @Test
    void shouldMaskEmailWithNullInput() {
        String masked = MaskedUserProfileDto.maskEmail(null);

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldMaskEmailWithNoAtSign() {
        String masked = MaskedUserProfileDto.maskEmail("invalidemail");

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldMaskPhone() {
        String masked = MaskedUserProfileDto.maskPhone("5551234567");

        assertThat(masked).isEqualTo("***4567");
    }

    @Test
    void shouldMaskPhoneWithNullInput() {
        String masked = MaskedUserProfileDto.maskPhone(null);

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldMaskPhoneWithShortInput() {
        // 4 chars or fewer returns "***"
        String masked = MaskedUserProfileDto.maskPhone("1234");

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldMaskFullName() {
        String masked = MaskedUserProfileDto.maskFullName("Alice Smith");

        assertThat(masked).isEqualTo("A***");
    }

    @Test
    void shouldMaskFullNameWithNullInput() {
        String masked = MaskedUserProfileDto.maskFullName(null);

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldMaskFullNameWithEmptyInput() {
        String masked = MaskedUserProfileDto.maskFullName("");

        assertThat(masked).isEqualTo("***");
    }

    @Test
    void shouldHandleNullsViaFactoryMethod() {
        MaskedUserProfileDto dto = MaskedUserProfileDto.of(1L, "testuser", null, null, null, LocalDateTime.now());

        assertThat(dto.getMaskedFullName()).isEqualTo("***");
        assertThat(dto.getMaskedEmail()).isEqualTo("***");
        assertThat(dto.getMaskedPhone()).isEqualTo("***");
    }

    @Test
    void shouldCreateDtoWithCorrectFields() {
        LocalDateTime now = LocalDateTime.now();
        MaskedUserProfileDto dto = MaskedUserProfileDto.of(
            42L, "testuser", "Bob Johnson", "bob@example.com", "5559876543", now);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getUsername()).isEqualTo("testuser");
        assertThat(dto.getMaskedFullName()).isEqualTo("B***");
        assertThat(dto.getMaskedEmail()).isEqualTo("bo***@example.com");
        assertThat(dto.getMaskedPhone()).isEqualTo("***6543");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
    }
}
