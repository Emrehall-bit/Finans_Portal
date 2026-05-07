package com.emrehalli.financeportal.market.support;

import com.emrehalli.financeportal.market.domain.enums.InstrumentType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class InstrumentTypeAliasResolver {

    private static final Map<String, InstrumentType> TYPE_ALIASES = Map.ofEntries(
            Map.entry("FOREX", InstrumentType.FOREX),
            Map.entry("CURRENCY", InstrumentType.CURRENCY),
            Map.entry("FX", InstrumentType.FX),
            Map.entry("COMMODITY", InstrumentType.COMMODITY),
            Map.entry("GOLD", InstrumentType.GOLD),
            Map.entry("STOCK", InstrumentType.STOCK),
            Map.entry("FUND", InstrumentType.FUND),
            Map.entry("CRYPTO", InstrumentType.CRYPTO),
            Map.entry("MACRO", InstrumentType.MACRO_INDICATOR),
            Map.entry("MACRO_INDICATOR", InstrumentType.MACRO_INDICATOR),
            Map.entry("INDEX", InstrumentType.INDEX),
            Map.entry("BOND", InstrumentType.BOND),
            Map.entry("UNKNOWN", InstrumentType.UNKNOWN)
    );

    public InstrumentType resolve(String rawType) {
        if (rawType == null) {
            throw new IllegalArgumentException("Instrument type cannot be blank");
        }

        InstrumentType resolvedType = TYPE_ALIASES.get(rawType.trim().toUpperCase(Locale.ROOT));
        if (resolvedType == null) {
            throw new IllegalArgumentException("No enum constant " + InstrumentType.class.getName() + "." + rawType.trim().toUpperCase(Locale.ROOT));
        }
        return resolvedType;
    }

    public Set<InstrumentType> compatibleTypes(InstrumentType requestedType) {
        if (requestedType == null) {
            return Set.of();
        }

        return switch (requestedType) {
            case FOREX, CURRENCY, FX -> EnumSet.of(InstrumentType.FOREX, InstrumentType.CURRENCY, InstrumentType.FX);
            case COMMODITY, GOLD -> EnumSet.of(InstrumentType.COMMODITY, InstrumentType.GOLD);
            default -> EnumSet.of(requestedType);
        };
    }
}
