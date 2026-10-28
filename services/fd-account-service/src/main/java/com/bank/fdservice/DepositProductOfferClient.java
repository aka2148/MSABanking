package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DepositProductOfferClient {
    private final RestClient products;
    public DepositProductOfferClient(@Value("${app.product.base-url:http://localhost:8082}") String url) { products=RestClient.create(url); }
    public JsonNode get(String family,long id,String authorization) {
        try {
            JsonNode offer=products.get().uri("/api/v1/customer-products/{family}/{id}",family,id)
                    .header("Authorization",authorization==null?"":authorization).retrieve().body(JsonNode.class);
            if(offer==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product catalog returned no offer");
            return offer;
        } catch(RestClientResponseException ex) {
            if(ex.getStatusCode().value()==404) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Product is unpublished, incomplete, or unavailable");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product catalog could not validate this request");
        }
    }
}
