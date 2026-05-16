package com.example.shiftscheduler; 

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Employee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String role; // e.g., "Manager" or "Staff"
}