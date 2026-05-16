package com.example.shiftscheduler;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    private boolean tradeOpen = false; 
    private String tradeNotes;         

    // Fields to handle the two-way swap offer
    private String offeredShiftIds; 
    private String offeredByUsername; 
}