package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.service.CustomerService;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.repository.FdUserReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the authenticated local customer to the FD holder reference. */
@Service
public class FdActorResolver {
    private final CustomerService customerService;
    private final FdUserReferenceRepository referenceRepository;

    public FdActorResolver(CustomerService customerService, FdUserReferenceRepository referenceRepository) {
        this.customerService = customerService;
        this.referenceRepository = referenceRepository;
    }

    @Transactional
    public FdUserReference resolve(String username) {
        if (username == null || username.isBlank()) {
            throw new ResourceNotFoundException("Authenticated customer is required");
        }
        CustomerProfile profile = customerService.getProfileEntityByUsername(username);
        if (profile.getUser() == null || profile.getUser().getUserUuid() == null) {
            throw new ResourceNotFoundException("Authenticated customer has no stable user identity");
        }
        return referenceRepository.findByUserUuid(profile.getUser().getUserUuid())
                .orElseGet(() -> createReference(profile));
    }

    private FdUserReference createReference(CustomerProfile profile) {
        FdUserReference reference = new FdUserReference();
        reference.setUserUuid(profile.getUser().getUserUuid());
        reference.setCustomerNumber(profile.getCustomerNumber());
        reference.setFullName(profile.getUser().getUsername());
        reference.setStatus(profile.getUser().getStatus());
        return referenceRepository.save(reference);
    }
}
