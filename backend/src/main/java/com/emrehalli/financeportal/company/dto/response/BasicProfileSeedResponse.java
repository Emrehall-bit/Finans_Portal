package com.emrehalli.financeportal.company.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BasicProfileSeedResponse {

    private int created;
    /** Zayıf profil (company_name=ticker_code) iken seed map ile düzeltilen kayıt sayısı. */
    private int updatedWeak;
    private int skippedExisting;
    private List<BasicProfileSeedError> errors;
}

