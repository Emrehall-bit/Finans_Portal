package com.emrehalli.financeportal.company.kap.client;

import com.emrehalli.financeportal.company.kap.dto.DisclosureFailedItemDto;
import com.emrehalli.financeportal.company.kap.dto.KapDisclosureDto;

import java.util.List;

public record KapDisclosureProviderResult(
        List<KapDisclosureDto> disclosures,
        List<DisclosureFailedItemDto> failedItems
) {}



