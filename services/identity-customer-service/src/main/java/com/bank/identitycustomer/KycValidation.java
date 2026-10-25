package com.bank.identitycustomer;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

final class KycValidation {
    private KycValidation() {}
    static final List<String> FIELDS = List.of("dateOfBirth", "gender", "relationship", "fatherOrSpouseName",
            "occupation", "countryOfOrigin", "proofOfIdType", "proofOfIdValue", "idIssueDate");
    // ISO 3166-1 alpha-2; explicit set, independent of runtime locale extensions.
    static final Set<String> COUNTRIES = Set.of(("AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW").split(" "));
    static final Map<String, String> ID_PATTERNS = Map.of(
            "AADHAAR", "^[2-9][0-9]{11}$", "PAN", "^[A-Z]{5}[0-9]{4}[A-Z]$",
            "PASSPORT", "^[A-Z][0-9]{7}$", "DRIVING_LICENSE", "^[A-Z0-9-]{8,20}$", "VOTER_ID", "^[A-Z]{3}[0-9]{7}$");

    static boolean validId(String type, String value) {
        return value != null && type != null && ID_PATTERNS.containsKey(type) && value.matches(ID_PATTERNS.get(type));
    }

    static boolean safeText(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max
                && value.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '<' || c == '>');
    }

    static String error(String field, String value, Map<String, String> values) {
        if (value == null) return null; // Clearing is allowed in drafts.
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return switch (field) {
            case "dateOfBirth" -> {
                LocalDate date = date(value);
                yield date != null && !date.isBefore(LocalDate.of(1900, 1, 1)) && !date.isAfter(today.minusYears(18))
                        ? null : "You must be at least 18.";
            }
            case "gender" -> Set.of("MALE", "FEMALE", "OTHER").contains(value) ? null : "Select a valid option.";
            case "relationship" -> Set.of("FATHER", "SPOUSE").contains(value) ? null : "Select a valid option.";
            case "occupation" -> Set.of("STUDENT", "SALARIED", "SELF_EMPLOYED", "BUSINESS", "PROFESSIONAL", "HOMEMAKER", "RETIRED", "OTHER").contains(value) ? null : "Select a valid option.";
            case "countryOfOrigin" -> COUNTRIES.contains(value) ? null : "Select a valid country.";
            case "fatherOrSpouseName" -> safeText(value, 2, 100) && value.codePoints().anyMatch(Character::isLetter)
                    ? null : "Enter a name of 2–100 characters containing a letter, without markup or control characters.";
            case "proofOfIdType" -> ID_PATTERNS.containsKey(value) ? null : "Select a valid option.";
            case "proofOfIdValue" -> validId(values.get("proofOfIdType"), value) ? null : "Enter a valid ID number for the selected proof type.";
            case "idIssueDate" -> {
                LocalDate issue = date(value), birth = date(values.get("dateOfBirth"));
                yield issue != null && !issue.isAfter(today) && birth != null && issue.isAfter(birth)
                        ? null : "Enter an issue date after birth and not in the future.";
            }
            default -> throw new IllegalArgumentException("Unknown KYC field");
        };
    }

    private static LocalDate date(String value) {
        try { return value != null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") ? LocalDate.parse(value) : null; }
        catch (java.time.DateTimeException ex) { return null; }
    }
}
