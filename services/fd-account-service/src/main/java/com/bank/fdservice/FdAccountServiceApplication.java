package com.bank.fdservice;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
public class FdAccountServiceApplication { public static void main(String[] args){SpringApplication.run(FdAccountServiceApplication.class,args);} }
