package com.reclaim.portal.appointments.controller;

import com.reclaim.portal.appointments.entity.Appointment;
import com.reclaim.portal.appointments.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentApiController {

    private final AppointmentService appointmentService;

    public AppointmentApiController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * GET /api/appointments/available?date=YYYY-MM-DD&type=PICKUP|DROPOFF
     * Returns available appointment slots for the given date and type.
     */
    @GetMapping("/available")
    public ResponseEntity<List<Appointment>> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String type) {
        List<Appointment> slots = appointmentService.getAvailableSlots(date, type);
        return ResponseEntity.ok(slots);
    }

    /**
     * POST /api/appointments/generate?date=YYYY-MM-DD
     * Admin-only: generate appointment slots for the given date.
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> generateSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        appointmentService.generateSlots(date);
        return ResponseEntity.ok().build();
    }
}
