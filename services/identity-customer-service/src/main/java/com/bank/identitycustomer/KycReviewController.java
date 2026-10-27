package com.bank.identitycustomer;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers/kyc/submissions")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class KycReviewController {
    private final KycReviewService service;
    public KycReviewController(KycReviewService service){this.service=service;}
    @GetMapping public KycReviewService.Page queue(@RequestParam(defaultValue="AWAITING_REVIEW") String status,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.queue(status,search,page,size);}
    @GetMapping("/{customerId}") public Map<String,Object> detail(@PathVariable long customerId,Authentication actor){return service.detail(customerId,actor.getName());}
    @GetMapping("/{customerId}/document") public ResponseEntity<byte[]> document(@PathVariable long customerId,Authentication actor){
        var document=service.document(customerId,actor.getName());
        return ResponseEntity.ok().header("Content-Type",document.contentType()).header("Content-Disposition","inline; filename=kyc-document")
                .header("X-Content-Type-Options","nosniff").header("Cache-Control","no-store").body(document.bytes());
    }
    @PostMapping("/{customerId}/decision") public ResponseEntity<Void> decide(@PathVariable long customerId,Authentication actor,@RequestBody Decision request){service.decide(customerId,actor.getName(),request.decision(),request.reason());return ResponseEntity.noContent().build();}
    public record Decision(String decision,String reason){}
}
