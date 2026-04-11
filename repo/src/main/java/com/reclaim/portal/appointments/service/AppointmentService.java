package com.reclaim.portal.appointments.service;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.repository.AppointmentRepository;
import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PICKUP = "PICKUP";
    private static final String DROPOFF = "DROPOFF";

    private final AppointmentRepository appointmentRepository;
    private final ReclaimProperties reclaimProperties;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              ReclaimProperties reclaimProperties) {
        this.appointmentRepository = appointmentRepository;
        this.reclaimProperties = reclaimProperties;
    }

    /**
     * Return available slots for a given date and type after enforcing advance booking rules.
     * For today, also filter out slots whose start time is not at least minAdvanceHours from now.
     */
    public List<Appointment> getAvailableSlots(LocalDate date, String type) {
        ReclaimProperties.Appointments cfg = reclaimProperties.getAppointments();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        LocalDate minDate = today.plusDays(0);
        LocalDate maxDate = today.plusDays(cfg.getMaxAdvanceDays());

        if (date.isBefore(minDate) || date.isAfter(maxDate)) {
            throw new BusinessRuleException(
                "Appointment date must be between today and " + cfg.getMaxAdvanceDays() + " days from now");
        }

        // Auto-generate slots for the day if none exist yet
        generateSlots(date);

        List<Appointment> slots = appointmentRepository
            .findByAppointmentDateAndAppointmentType(date, type);

        LocalDateTime minAllowed = now.plusHours(cfg.getMinAdvanceHours());

        return slots.stream()
            .filter(a -> a.getSlotsBooked() < a.getSlotsAvailable())
            .filter(a -> {
                // For today specifically, also check that start time is after now+minAdvanceHours
                if (date.equals(today)) {
                    LocalTime slotStart = LocalTime.parse(a.getStartTime(), TIME_FMT);
                    LocalDateTime slotDateTime = date.atTime(slotStart);
                    return !slotDateTime.isBefore(minAllowed);
                }
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Generate 30-minute slots for a date if none exist yet.
     */
    @Transactional
    public void generateSlots(LocalDate date) {
        if (appointmentRepository.countByAppointmentDate(date) > 0) {
            return;
        }

        ReclaimProperties.Appointments cfg = reclaimProperties.getAppointments();
        List<Appointment> toSave = new ArrayList<>();

        LocalTime cursor = LocalTime.of(cfg.getBusinessStartHour(), 0);
        LocalTime end = LocalTime.of(cfg.getBusinessEndHour(), 0);

        while (cursor.isBefore(end)) {
            LocalTime slotEnd = cursor.plusMinutes(cfg.getSlotMinutes());
            if (slotEnd.isAfter(end)) {
                break;
            }

            for (String type : List.of(PICKUP, DROPOFF)) {
                Appointment a = new Appointment();
                a.setAppointmentDate(date);
                a.setStartTime(cursor.format(TIME_FMT));
                a.setEndTime(slotEnd.format(TIME_FMT));
                a.setAppointmentType(type);
                a.setSlotsAvailable(PICKUP.equals(type)
                    ? cfg.getPickupCapacity()
                    : cfg.getDropoffCapacity());
                a.setSlotsBooked(0);
                a.setCreatedAt(LocalDateTime.now());
                toSave.add(a);
            }

            cursor = slotEnd;
        }

        appointmentRepository.saveAll(toSave);
    }

    /**
     * Increment slotsBooked for the given appointment after checking capacity.
     */
    @Transactional
    public Appointment bookSlot(Long appointmentId) {
        Appointment appointment = findById(appointmentId);
        if (appointment.getSlotsBooked() >= appointment.getSlotsAvailable()) {
            throw new BusinessRuleException("No available capacity for appointment id: " + appointmentId);
        }
        appointment.setSlotsBooked(appointment.getSlotsBooked() + 1);
        return appointmentRepository.save(appointment);
    }

    /**
     * Decrement slotsBooked (minimum 0) for the given appointment.
     */
    @Transactional
    public Appointment releaseSlot(Long appointmentId) {
        Appointment appointment = findById(appointmentId);
        int current = appointment.getSlotsBooked();
        appointment.setSlotsBooked(Math.max(0, current - 1));
        return appointmentRepository.save(appointment);
    }

    /**
     * Validate that a date+startTime combination satisfies advance booking constraints.
     */
    public void validateAppointmentTime(LocalDate date, String startTime) {
        ReclaimProperties.Appointments cfg = reclaimProperties.getAppointments();
        LocalDateTime now = LocalDateTime.now();

        LocalTime time = LocalTime.parse(startTime, TIME_FMT);
        LocalDateTime appointmentDateTime = date.atTime(time);

        LocalDateTime minAllowed = now.plusHours(cfg.getMinAdvanceHours());
        LocalDateTime maxAllowed = now.plusDays(cfg.getMaxAdvanceDays());

        if (appointmentDateTime.isBefore(minAllowed)) {
            throw new BusinessRuleException(
                "Appointment must be at least " + cfg.getMinAdvanceHours() + " hours in advance");
        }
        if (appointmentDateTime.isAfter(maxAllowed)) {
            throw new BusinessRuleException(
                "Appointment cannot be more than " + cfg.getMaxAdvanceDays() + " days in advance");
        }
    }

    public Appointment findById(Long id) {
        return appointmentRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Appointment", id));
    }
}
