package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Paginated customer FD account listing. */
public record FdAccountListResponse(
        List<FdAccountSummaryResponse> data,
        Pagination pagination) {

    public record Pagination(
            int page,
            @JsonProperty("page_size") int pageSize,
            @JsonProperty("total_records") long totalRecords) {}
}
