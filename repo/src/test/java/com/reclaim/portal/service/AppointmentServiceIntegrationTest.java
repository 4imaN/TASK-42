package com.reclaim.portal.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.appointments.service.AppointmentService;
import com.reclaim.portal.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppointmentServiceIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Test
    void shouldGenerateSlots() {
        LocalDate futureDate = LocalDate.now().plusDays(5);

        // Generate slots (idempotent — safe if prior tests already seeded this date).
        appointmentService.generateSlots(futureDate);

        long count = appointmentRepository.countByAppointmentDate(futureDate);
        // Business hours 8-18, 30-min slots => 20 slots, 2 types = 40 slots
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void shouldNotDuplicateSlotsOnSecondGenerate() {
        LocalDate futureDate = LocalDate.now().plusDays(6);

        appointmentService.generateSlots(futureDate);
        long countAfterFirst = appointmentRepository.countByAppointmentDate(futureDate);

        appointmentService.generateSlots(futureDate); // second call should be a no-op
        long countAfterSecond = appointmentRepository.countByAppointmentDate(futureDate);

        assertThat(countAfterFirst).isEqualTo(countAfterSecond);
    }

    @Test
    void shouldBookSlot() {
        LocalDate futureDate = LocalDate.now().plusDays(3);
        appointmentService.generateSlots(futureDate);

        List<Appointment> slots = appointmentRepository
            .findByAppointmentDateAndAppointmentType(futureDate, "PICKUP");
        Appointment slot = slots.get(0);
        int initialBooked = slot.getSlotsBooked();

        Appointment booked = appointmentService.bookSlot(slot.getId());

        assertThat(booked.getSlotsBooked()).isEqualTo(initialBooked + 1);
    }

    @Test
    void shouldRejectWhenFull() {
        LocalDate futureDate = LocalDate.now().plusDays(4);

        // Create a fully booked slot
        Appointment full = new Appointment();
        full.setAppointmentDate(futureDate);
        full.setStartTime("09:00");
        full.setEndTime("09:30");
        full.setAppointmentType("PICKUP");
        full.setSlotsAvailable(1);
        full.setSlotsBooked(1); // already at capacity
        full.setCreatedAt(LocalDateTime.now());
        full = appointmentRepository.save(full);

        final Long fullId = full.getId();
        assertThatThrownBy(() -> appointmentService.bookSlot(fullId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("capacity");
    }

    @Test
    void shouldValidateMinAdvance() {
        // 30 minutes from now is less than 2-hour minimum
        LocalDate today = LocalDate.now();
        String soonTime = java.time.LocalTime.now().plusMinutes(30).format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        assertThatThrownBy(() -> appointmentService.validateAppointmentTime(today, soonTime))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("at least");
    }

    @Test
    void shouldValidateMaxAdvance() {
        // 30 days from now exceeds the 14-day maximum
        LocalDate tooFar = LocalDate.now().plusDays(30);

        assertThatThrownBy(() -> appointmentService.validateAppointmentTime(tooFar, "10:00"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("days in advance");
    }

    @Test
    void shouldReleaseSlot() {
        // Seed a DROPOFF slot directly rather than relying on generateSlots — that method
        // early-returns if any slot already exists for the date, and HTTP-level tests in
        // the same JVM run can commit slots that survive into this test class.
        LocalDate futureDate = LocalDate.now().plusDays(3);
        Appointment slot = new Appointment();
        slot.setAppointmentDate(futureDate);
        slot.setStartTime("10:00");
        slot.setEndTime("10:30");
        slot.setAppointmentType("DROPOFF");
        slot.setSlotsAvailable(5);
        slot.setSlotsBooked(0);
        slot.setCreatedAt(LocalDateTime.now());
        slot = appointmentRepository.save(slot);

        int initialBooked = slot.getSlotsBooked();

        // Book first, then release
        appointmentService.bookSlot(slot.getId());
        Appointment released = appointmentService.releaseSlot(slot.getId());

        assertThat(released.getSlotsBooked()).isEqualTo(initialBooked);
    }

    @Test
    void shouldRejectPastDateForAvailableSlots() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> appointmentService.getAvailableSlots(pastDate, "PICKUP"))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldGetAvailableSlotsForFutureDate() {
        LocalDate futureDate = LocalDate.now().plusDays(5);

        List<Appointment> slots = appointmentService.getAvailableSlots(futureDate, "PICKUP");

        // All returned slots should have available capacity
        assertThat(slots).isNotEmpty();
        assertThat(slots).allMatch(a -> a.getSlotsBooked() < a.getSlotsAvailable());
        assertThat(slots).allMatch(a -> "PICKUP".equals(a.getAppointmentType()));
    }

    @Test
    void shouldGetAvailableSlotsForDropoffType() {
        LocalDate futureDate = LocalDate.now().plusDays(7);

        // Seed a DROPOFF slot directly — generateSlots is a no-op if any slot already
        // exists for the date (including PICKUP-only state leaked from HTTP-level tests).
        Appointment dropoff = new Appointment();
        dropoff.setAppointmentDate(futureDate);
        dropoff.setStartTime("11:00");
        dropoff.setEndTime("11:30");
        dropoff.setAppointmentType("DROPOFF");
        dropoff.setSlotsAvailable(5);
        dropoff.setSlotsBooked(0);
        dropoff.setCreatedAt(LocalDateTime.now());
        appointmentRepository.save(dropoff);

        List<Appointment> slots = appointmentService.getAvailableSlots(futureDate, "DROPOFF");

        assertThat(slots).isNotEmpty();
        assertThat(slots).allMatch(a -> "DROPOFF".equals(a.getAppointmentType()));
    }

    @Test
    void shouldNotReturnFullyBookedSlotsInGetAvailable() {
        LocalDate futureDate = LocalDate.now().plusDays(8);
        appointmentService.generateSlots(futureDate);

        // Fully book all PICKUP slots
        List<Appointment> pickupSlots = appointmentRepository
            .findByAppointmentDateAndAppointmentType(futureDate, "PICKUP");
        for (Appointment slot : pickupSlots) {
            slot.setSlotsBooked(slot.getSlotsAvailable());
            appointmentRepository.save(slot);
        }

        List<Appointment> available = appointmentService.getAvailableSlots(futureDate, "PICKUP");
        assertThat(available).isEmpty();
    }

    @Test
    void shouldReleaseSlotToMinimumZero() {
        Appointment slot = new Appointment();
        slot.setAppointmentDate(LocalDate.now().plusDays(5));
        slot.setStartTime("14:00");
        slot.setEndTime("14:30");
        slot.setAppointmentType("DROPOFF");
        slot.setSlotsAvailable(5);
        slot.setSlotsBooked(0); // already at zero
        slot.setCreatedAt(LocalDateTime.now());
        slot = appointmentRepository.save(slot);

        Appointment released = appointmentService.releaseSlot(slot.getId());

        // Should remain at 0, not go negative
        assertThat(released.getSlotsBooked()).isEqualTo(0);
    }

    @Test
    void shouldRejectTooFarDateForAvailableSlots() {
        LocalDate tooFar = LocalDate.now().plusDays(30); // exceeds 14-day max

        assertThatThrownBy(() -> appointmentService.getAvailableSlots(tooFar, "PICKUP"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("14 days");
    }

    @Test
    void shouldFindAppointmentById() {
        LocalDate futureDate = LocalDate.now().plusDays(9);
        appointmentService.generateSlots(futureDate);

        List<Appointment> slots = appointmentRepository
            .findByAppointmentDateAndAppointmentType(futureDate, "PICKUP");
        Appointment slot = slots.get(0);

        Appointment found = appointmentService.findById(slot.getId());
        assertThat(found.getId()).isEqualTo(slot.getId());
    }

    @Test
    void shouldThrowForUnknownAppointmentId() {
        assertThatThrownBy(() -> appointmentService.findById(999999L))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }
}
