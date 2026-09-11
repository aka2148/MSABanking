package com.bank.customer.controller;
import com.bank.customer.service.ExternalUserService;import jakarta.validation.constraints.Positive;import org.springframework.http.ResponseEntity;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;import java.util.Map;
@RestController @RequestMapping("/users") public class ExternalUserController{
 private final ExternalUserService service;public ExternalUserController(ExternalUserService service){this.service=service;}
 @GetMapping("/{id}") @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')") public ResponseEntity<Map<String,Object>> getUser(@PathVariable @Positive Long id,@RequestHeader(value="Authorization",required=false) String authorization){return service.getUser(id,authorization);}
}
