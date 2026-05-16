package com.example.shiftscheduler; 

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    
    @Query("SELECT COUNT(s) FROM Shift s WHERE s.employee = :employee AND s.startTime < :endTime AND s.endTime > :startTime")
    long countOverlappingShifts(@Param("employee") Employee employee, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);
}