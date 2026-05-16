package com.example.shiftscheduler;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    // Pulling passwords  from application.properties
    @Value("${app.boss.password}")
    private String bossPassword;

    @Value("${app.emp.default-password}")
    private String empPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.loginView(LoginView.class);
        }).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        UserDetails boss = User.withUsername("boss").password(encoder.encode(bossPassword)).roles("MANAGER").build();
        
        UserDetails emp1 = User.withUsername("emp1").password(encoder.encode(empPassword)).roles("USER").build();
        UserDetails emp2 = User.withUsername("emp2").password(encoder.encode(empPassword)).roles("USER").build();
        UserDetails emp3 = User.withUsername("emp3").password(encoder.encode(empPassword)).roles("USER").build();
        UserDetails emp4 = User.withUsername("emp4").password(encoder.encode(empPassword)).roles("USER").build();
        UserDetails emp5 = User.withUsername("emp5").password(encoder.encode(empPassword)).roles("USER").build();

        return new InMemoryUserDetailsManager(boss, emp1, emp2, emp3, emp4, emp5);
    }
}