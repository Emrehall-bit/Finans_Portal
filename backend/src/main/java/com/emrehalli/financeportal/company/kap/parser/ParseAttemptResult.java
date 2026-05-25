package com.emrehalli.financeportal.company.kap.parser;

import com.emrehalli.financeportal.company.kap.dto.ParseFailureDto;
import com.emrehalli.financeportal.company.domain.enums.ParseStatus;

public record ParseAttemptResult(ParseStatus status, ParseFailureDto failure, int matchedItemCount, int savedValueCount, int updatedValueCount) {

    public static ParseAttemptResult ok(ParseStatus status) {
        return ok(status, 0, 0, 0);
    }

    public static ParseAttemptResult ok(ParseStatus status, int matchedItemCount, int savedValueCount) {
        return ok(status, matchedItemCount, savedValueCount, 0);
    }

    public static ParseAttemptResult ok(ParseStatus status, int matchedItemCount, int savedValueCount, int updatedValueCount) {
        return new ParseAttemptResult(status, null, matchedItemCount, savedValueCount, updatedValueCount);
    }

    public static ParseAttemptResult failed(ParseFailureDto failure) {
        return failed(failure, 0, 0, 0);
    }

    public static ParseAttemptResult failed(ParseFailureDto failure, int matchedItemCount, int savedValueCount) {
        return failed(failure, matchedItemCount, savedValueCount, 0);
    }

    public static ParseAttemptResult failed(ParseFailureDto failure, int matchedItemCount, int savedValueCount, int updatedValueCount) {
        return new ParseAttemptResult(ParseStatus.FAILED, failure, matchedItemCount, savedValueCount, updatedValueCount);
    }
}



