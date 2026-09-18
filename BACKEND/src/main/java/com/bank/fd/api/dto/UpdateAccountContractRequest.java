package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Only explicitly mutable account instructions belong in this patch contract. */
public class UpdateAccountContractRequest {

    @JsonProperty("auto_renewal")
    private Boolean autoRenewal;

    public Boolean getAutoRenewal() {
        return autoRenewal;
    }

    public void setAutoRenewal(Boolean autoRenewal) {
        this.autoRenewal = autoRenewal;
    }
}
