package com.example.shiftscheduler; 

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

@Configuration
public class DataLoader {

    @Bean
    CommandLineRunner loadData(EmployeeRepository empRepo, ShiftRepository shiftRepo) {
        return args -> {
            if (empRepo.count() == 0) {
                Employee emp1 = new Employee();
                emp1.setName("Abdolhamid Agah");
                emp1.setRole("Staff");
                empRepo.save(emp1);

                Employee emp2 = new Employee();
                emp2.setName("Katharina Wiemers");
                emp2.setRole("Manager");
                empRepo.save(emp2);

                Shift shift = new Shift();
                shift.setEmployee(emp1);
                shift.setStartTime(LocalDateTime.now().plusDays(1).withHour(8).withMinute(0));
                shift.setEndTime(LocalDateTime.now().plusDays(1).withHour(16).withMinute(0));
                shiftRepo.save(shift);
            }
        };
    }
}