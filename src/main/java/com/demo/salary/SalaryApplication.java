package com.demo.salary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SalaryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalaryApplication.class, args);
    }

    @GetMapping("/api/v1/salary")
    public String getSalary() {
        return "✅ Salary API v1.0 - Successfully running inside an auto-deployed AMI!";
    }
}
