package com.emrehalli.financeportal.company.provider.kap;

import com.emrehalli.financeportal.company.dto.DisclosureFailedItemDto;
import com.emrehalli.financeportal.company.provider.kap.dto.KapDisclosureDto;

import java.util.List;

public record KapDisclosureProviderResult(
        List<KapDisclosureDto> disclosures,
        List<DisclosureFailedItemDto> failedItems
) {}
