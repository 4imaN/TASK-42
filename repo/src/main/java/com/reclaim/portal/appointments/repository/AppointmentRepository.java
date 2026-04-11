package com.reclaim.portal.appointments.repository;

import com.reclaim.portal.appointments.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByAppointmentDateAndAppointmentType(LocalDate appointmentDate, String appointmentType);

    List<Appointment> findByAppointmentDateBetweenAndAppointmentType(
            LocalDate startDate, LocalDate endDate, String appointmentType);

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate BETWEEN :startDate AND :endDate " +
           "AND a.appointmentType = :appointmentType AND a.slotsBooked < a.slotsAvailable")
    List<Appointment> findAvailableByDateRangeAndType(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("appointmentType") String appointmentType);

    long countByAppointmentDate(LocalDate appointmentDate);
}
