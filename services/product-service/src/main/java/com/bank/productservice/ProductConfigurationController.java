package com.bank.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/product-configurations")
public class ProductConfigurationController {
    private static final Set<String> FAMILIES = Set.of("LOAN", "CASA", "FIXED_DEPOSIT", "FOREX");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN_INCLUSIVE", "IN");
    private static final Map<String, Fact> FACTS = Map.ofEntries(
            Map.entry("CUSTOMER_AGE", new Fact("NUMBER", true, null)),
            Map.entry("KYC_STATUS", new Fact("STRING", true, null)),
            Map.entry("RISK_CATEGORY", new Fact("STRING", true, null)),
            Map.entry("VERIFIED_IDENTITY", new Fact("BOOLEAN", true, null)),
            Map.entry("ANNUAL_INCOME", new Fact("NUMBER", false, "Customer API does not provide verified income")),
            Map.entry("RESIDENCY", new Fact("STRING", false, "Customer API does not provide residency")),
            Map.entry("PAN_VERIFIED", new Fact("BOOLEAN", false, "Customer API does not expose a verified PAN fact")));

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FdComplianceService compliance;

    public ProductConfigurationController(JdbcTemplate jdbc, ObjectMapper json, FdComplianceService compliance) {
        this.jdbc = jdbc;
        this.json = json;
        this.compliance = compliance;
    }

    @GetMapping("/metadata")
    public BuilderMetadata metadata(@RequestParam(required = false) Integer categoryId) {
        List<Category> categories = jdbc.query("SELECT pc_category_id,pc_category_code,pc_category_name,pc_parent_category_id FROM product_category ORDER BY pc_category_name,pc_category_id",
                (r, n) -> new Category(r.getInt(1), r.getString(2), r.getString(3), r.getObject(4) == null ? null : ((Number) r.getObject(4)).intValue()));
        List<CategoryOption> categoryOptions = categories.stream().map(c -> new CategoryOption(c.id(), c.code(), c.name(), c.parentId(), family(c.id(), categories))).toList();
        Set<Integer> inherited = categoryId == null ? Set.of() : lineage(categoryId, categories);
        List<AttributeOption> attributes = jdbc.query("SELECT ad_attribute_def_id,ad_category_id,ad_attribute_key,ad_data_type,ad_display_name,ad_is_required,ad_choice_values,ad_unit,ad_default_value,ad_min_value,ad_max_value,ad_display_order,ad_behavior_key FROM attribute_definitions ORDER BY ad_display_order,ad_attribute_key",
                (r, n) -> new AttributeOption(r.getInt(1), r.getInt(2), r.getString(3), r.getString(4), r.getString(5), r.getBoolean(6), split(r.getString(7)), r.getString(8), r.getString(9), r.getBigDecimal(10), r.getBigDecimal(11), r.getInt(12), r.getString(13), categoryId != null && r.getInt(2) != categoryId))
                .stream().filter(a -> categoryId == null || inherited.contains(a.categoryId())).toList();
        List<CurrencyOption> currencies = jdbc.query("SELECT cur_currency_id,cur_iso_code,cur_currency_name,cur_currency_symbol,cur_decimal_places FROM currency WHERE cur_is_active=1 ORDER BY cur_iso_code",
                (r, n) -> new CurrencyOption(r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getInt(5)));
        List<RoleOption> roles = jdbc.query("SELECT rot_role_type_id,rot_role_code,rot_description FROM role_type ORDER BY rot_role_type_id",
                (r, n) -> new RoleOption(r.getInt(1), r.getString(2), r.getString(3)));
        List<FactOption> facts = FACTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> new FactOption(e.getKey(), e.getValue().type(), operators(e.getValue().type()), choices(e.getKey()), e.getValue().executable(), e.getValue().dependency())).toList();
        return new BuilderMetadata(categoryOptions, currencies, attributes, roles, facts, OPERATORS.stream().sorted().toList(),
                List.of("INTEREST_RATE", "CREDIT_INTEREST_RATE", "MARKUP_RATE"),
                List.of("CASA_DAILY_BALANCE", "FD_QUARTERLY", "FOREX_REFERENCE_MARKUP", "LOAN_REDUCING_BALANCE"));
    }

    @GetMapping
    public List<ConfigurationSummary> list() {
        List<Category> categories = jdbc.query("SELECT pc_category_id,pc_category_code,pc_category_name,pc_parent_category_id FROM product_category",
                (r, n) -> new Category(r.getInt(1), r.getString(2), r.getString(3), r.getObject(4) == null ? null : ((Number) r.getObject(4)).intValue()));
        return jdbc.query("SELECT p.pro_product_id,p.pro_product_code,p.pro_product_name,p.pro_category_id,p.pro_status,p.pro_base_currency_code,(SELECT COUNT(*) FROM product_configuration_revision r WHERE r.pcr_product_id=p.pro_product_id) revision,(SELECT GROUP_CONCAT(c.cur_iso_code ORDER BY c.cur_iso_code) FROM product_currency_config x JOIN currency c ON c.cur_currency_id=x.pcc_currency_id WHERE x.pcc_product_id=p.pro_product_id) currencies FROM product p ORDER BY p.pro_product_id DESC",
                (r, n) -> summary(r.getInt(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5), r.getString(6), r.getInt(7), r.getString(8), categories));
    }

    @PostMapping("/validate-identity")
    public Map<String,String> validateIdentity(@RequestBody IdentityRequest request) {
        Map<String,String> errors = new LinkedHashMap<>();
        if (!ProductTextRules.validName(request.productName(),255)) errors.put("productName","Enter a valid product name (3-255 characters including a letter).");
        if (!ProductTextRules.validDescription(request.description())) errors.put("description","Enter 10-1000 characters including a letter, without markup or control characters.");
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        if (request.effectiveFrom()==null) errors.put("effectiveFrom","Effective-from date is required.");
        else if (request.effectiveFrom().isBefore(today)) errors.put("effectiveFrom","Effective-from date must not be in the past.");
        if (request.effectiveTo()==null) errors.put("effectiveTo","Effective-to date is required.");
        else if (request.effectiveTo().isBefore(today)) errors.put("effectiveTo","Effective-to date must not be in the past.");
        if (request.effectiveFrom()!=null && request.effectiveTo()!=null && request.effectiveFrom().isAfter(request.effectiveTo())) errors.put("effectiveTo","Effective-to date must be on or after effective-from date.");
        return errors;
    }

    @PostMapping("/validate")
    public ValidationResult validate(@Valid @RequestBody ConfigurationRequest request) {
        List<String> errors = validationErrors(request, true);
        return new ValidationResult(errors.isEmpty(), errors);
    }

    @PostMapping("/preview")
    public PricingPreview preview(@Valid @RequestBody PreviewRequest request) {
        ConfigurationRequest config = request.configuration();
        List<String> errors = validationErrors(config, false);
        String family = safeFamily(config.categoryId());
        if (!errors.isEmpty()) return new PricingPreview("UNSUPPORTED", family, config.baseCurrencyCode(), config.baseCurrencyCode(), null, null, Map.of(), errors);
        Map<String, String> facts = request.facts() == null ? Map.of() : request.facts();
        List<String> missing = config.eligibilityRules() == null ? List.of() : config.eligibilityRules().stream().map(r -> norm(r.fact())).filter(f -> !facts.containsKey(f)).distinct().toList();
        if (!missing.isEmpty()) return new PricingPreview("INCOMPLETE", family, config.baseCurrencyCode(), config.baseCurrencyCode(), null, null, Map.of(), List.of("Missing customer/application facts: " + String.join(", ", missing)));
        List<EligibilityRule> rules = config.eligibilityRules() == null ? List.of() : config.eligibilityRules();
        boolean andMatch = rules.stream().filter(r -> !"OR".equals(norm(orDefault(r.combineLogic(), "AND")))).allMatch(r -> matches(r, facts.get(norm(r.fact()))));
        List<EligibilityRule> orRules = rules.stream().filter(r -> "OR".equals(norm(r.combineLogic()))).toList();
        if (!andMatch || (!orRules.isEmpty() && orRules.stream().noneMatch(r -> matches(r, facts.get(norm(r.fact()))))))
            return new PricingPreview("SUPPORTED", family, config.baseCurrencyCode(), config.baseCurrencyCode(), null, null, Map.of("eligible", BigDecimal.ZERO), List.of("The sample facts entered in this preview do not satisfy the configured eligibility rules. This does not indicate whether any real customer qualifies."));
        return previewPricing(config, facts, family);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ConfigurationSummary create(@Valid @RequestBody ConfigurationRequest request) {
        List<String> errors = validationErrors(request, true);
        if (!errors.isEmpty()) throw bad(String.join("; ", errors));
        Integer productId = insertProduct(request);
        saveConfiguration(productId, request);
        saveRevision(productId, "DRAFT", request);
        return findSummary(productId);
    }

    @PutMapping("/{productId}")
    @Transactional
    public ConfigurationSummary updateDraft(@PathVariable int productId, @Valid @RequestBody ConfigurationRequest request) {
        Map<String,Object> existing = jdbc.query("SELECT pro_product_code,pro_status FROM product WHERE pro_product_id=?", r -> {
            if (!r.next()) return null;
            return Map.of("code", r.getString(1), "status", r.getString(2));
        }, productId);
        if (existing == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found.");
        if (!"DRAFT".equalsIgnoreCase(Objects.toString(existing.get("status"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft product can be edited.");
        ConfigurationRequest normalized = new ConfigurationRequest(Objects.toString(existing.get("code")), request.productName(), request.categoryId(), request.description(), request.effectiveFrom(), request.effectiveTo(), request.baseCurrencyCode(), request.currencies(), request.attributes(), request.eligibilityRules(), request.pricingBands(), request.charges(), request.roleConstraints());
        List<String> errors = validationErrors(normalized, false);
        if (!errors.isEmpty()) throw bad(String.join("; ", errors));
        jdbc.update("UPDATE product SET pro_product_name=?,pro_category_id=?,pro_base_currency_code=?,pro_effective_from=?,pro_effective_to=?,pro_description=?,pro_updated_by='PRODUCT_BUILDER',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",
                ProductTextRules.name(normalized.productName()), normalized.categoryId(), norm(normalized.baseCurrencyCode()), date(normalized.effectiveFrom()), date(normalized.effectiveTo()), normalized.description()==null?null:normalized.description().trim(), productId);
        jdbc.update("DELETE FROM product_role_config WHERE prc_product_id=?", productId);
        jdbc.update("DELETE FROM product_attribute_values WHERE pav_product_id=?", productId);
        jdbc.update("DELETE FROM rule_condition WHERE rc_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=?)", productId);
        jdbc.update("DELETE FROM rule_outcome WHERE ro_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=?)", productId);
        jdbc.update("DELETE FROM product_rule WHERE pr_product_id=?", productId);
        jdbc.update("DELETE FROM charge_slab WHERE cs_charge_id IN (SELECT cc_charge_id FROM charge_config WHERE cc_product_id=?)", productId);
        jdbc.update("DELETE FROM charge_config WHERE cc_product_id=?", productId);
        jdbc.update("DELETE FROM product_currency_config WHERE pcc_product_id=?", productId);
        saveConfiguration(productId, normalized);
        saveRevision(productId, "DRAFT", normalized);
        return findSummary(productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDraft(@PathVariable int productId) {
        String status = jdbc.query("SELECT pro_status FROM product WHERE pro_product_id=?", r -> r.next() ? r.getString(1) : null, productId);
        if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product was not found.");
        if (!"DRAFT".equalsIgnoreCase(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a draft product can be deleted.");

        jdbc.update("DELETE FROM product_configuration_revision WHERE pcr_product_id=?", productId);
        jdbc.update("DELETE FROM product_role_config WHERE prc_product_id=?", productId);
        jdbc.update("DELETE FROM product_attribute_values WHERE pav_product_id=?", productId);
        jdbc.update("DELETE FROM rule_condition WHERE rc_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=?)", productId);
        jdbc.update("DELETE FROM rule_outcome WHERE ro_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=?)", productId);
        jdbc.update("DELETE FROM product_rule WHERE pr_product_id=?", productId);
        jdbc.update("DELETE FROM charge_slab WHERE cs_charge_id IN (SELECT cc_charge_id FROM charge_config WHERE cc_product_id=?)", productId);
        jdbc.update("DELETE FROM charge_config WHERE cc_product_id=?", productId);
        jdbc.update("DELETE FROM product_currency_config WHERE pcc_product_id=?", productId);
        jdbc.update("DELETE FROM product_translation WHERE pt_product_id=?", productId);
        try {
            jdbc.update("DELETE FROM product WHERE pro_product_id=?", productId);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This draft is referenced and cannot be deleted.");
        }
    }

    @PostMapping("/{productId}/activate")
    @Transactional
    public ConfigurationSummary activate(@PathVariable int productId) {
        ConfigurationSummary current = findSummary(productId);
        String snapshot = jdbc.query("SELECT pcr_snapshot_json FROM product_configuration_revision WHERE pcr_product_id=? ORDER BY pcr_revision_no DESC LIMIT 1", r -> r.next() ? r.getString(1) : null, productId);
        if (snapshot == null) throw bad("Product has no product-builder configuration revision");
        try {
            ConfigurationRequest request = json.readValue(snapshot, ConfigurationRequest.class);
            List<String> errors = validationErrors(request, false);
            if (!errors.isEmpty()) throw bad(String.join("; ", errors));
            if ("FIXED_DEPOSIT".equals(safeFamily(request.categoryId()))) {
                ensureNoDuplicateActiveFd(productId, snapshot);
                compliance.validateProductScheduleForActivation(productId);
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not read product configuration revision", e); }
        if (!"ACTIVE".equalsIgnoreCase(current.status())) {
            jdbc.update("UPDATE product SET pro_status='ACTIVE',pro_event_version=pro_event_version+1,pro_updated_by='PRODUCT_SERVICE',pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?", productId);
            saveRevision(productId, "ACTIVE", readSnapshot(snapshot));
            writeOutbox(productId);
        }
        return findSummary(productId);
    }

    private void ensureNoDuplicateActiveFd(int productId, String snapshot) {
        JsonNode candidate = comparableConfiguration(snapshot);
        List<Map<String,Object>> active = jdbc.queryForList("SELECT p.pro_product_id,r.pcr_snapshot_json FROM product p JOIN product_configuration_revision r ON r.pcr_product_id=p.pro_product_id WHERE p.pro_status='ACTIVE' AND p.pro_product_id<>? AND r.pcr_status='ACTIVE' AND r.pcr_revision_no=(SELECT MAX(r2.pcr_revision_no) FROM product_configuration_revision r2 WHERE r2.pcr_product_id=p.pro_product_id)", productId);
        for (Map<String,Object> row : active) {
            String existingSnapshot = Objects.toString(row.get("pcr_snapshot_json"), "");
            try {
                ConfigurationRequest existing = json.readValue(existingSnapshot, ConfigurationRequest.class);
                if ("FIXED_DEPOSIT".equals(safeFamily(existing.categoryId())) && candidate.equals(comparableConfiguration(existingSnapshot)))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "An identical fixed-deposit configuration is already active (product " + row.get("pro_product_id") + "). Change its terms or deactivate it before activating this draft.");
            } catch (java.io.IOException e) { throw new IllegalStateException("Could not compare active fixed-deposit configuration", e); }
        }
    }

    private JsonNode comparableConfiguration(String snapshot) {
        try {
            ObjectNode node = (ObjectNode) json.readTree(snapshot);
            node.remove(List.of("productCode", "productName", "description"));
            sortConfigurationArrays(node);
            return node;
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not compare product configuration", e); }
    }

    private void sortConfigurationArrays(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> sortConfigurationArrays(field.getValue()));
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            List<JsonNode> values = new ArrayList<>();
            array.forEach(value -> { sortConfigurationArrays(value); values.add(value); });
            values.sort(Comparator.comparing(JsonNode::toString));
            array.removeAll();
            values.forEach(array::add);
        }
    }

    private List<String> validationErrors(ConfigurationRequest r, boolean checkCode) {
        List<String> errors = new ArrayList<>();
        if (r == null) return List.of("Product configuration is required");
        if (r.productCode()!=null && !r.productCode().isBlank() && !ProductTextRules.validCode(r.productCode())) errors.add("Use a code of 3-32 uppercase letters, digits or underscores, starting with a letter.");
        errors.addAll(validateIdentity(new IdentityRequest(r.productName(),r.description(),r.effectiveFrom(),r.effectiveTo())).values());
        String family = safeFamily(r.categoryId());
        if (!FAMILIES.contains(family)) errors.add("Category must inherit from Loan, CASA, Fixed Deposit or Forex");
        if (checkCode && r.productCode()!=null && !r.productCode().isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE UPPER(pro_product_code)=UPPER(?)", Integer.class, norm(r.productCode())) > 0) errors.add("Product code is already in use");
        Set<String> selected = new HashSet<>();
        if (r.currencies() == null || r.currencies().isEmpty()) errors.add("Select at least one supported currency");
        else for (CurrencyConfig c : r.currencies()) {
            String code = norm(c.currencyCode());
            if (!selected.add(code)) errors.add("Currency appears more than once: " + code);
            Integer scale = jdbc.query("SELECT cur_decimal_places FROM currency WHERE UPPER(cur_iso_code)=? AND cur_is_active=1", rs -> rs.next() ? rs.getInt(1) : null, code);
            if (scale == null) errors.add("Active currency is not configured: " + code);
            if (c.minimumAmount() == null || c.maximumAmount() == null || c.minimumAmount().signum() < 0 || c.maximumAmount().signum() <= 0 || c.minimumAmount().compareTo(c.maximumAmount()) > 0) errors.add("Currency amount limits are invalid for " + code);
            else if (scale != null && (c.minimumAmount().scale() > scale || c.maximumAmount().scale() > scale)) errors.add(code + " amounts support " + scale + " decimal places");
            if (!"CASA".equals(family) && c.minimumAmount() != null && c.minimumAmount().signum() <= 0) errors.add(family + " minimum amounts must be greater than zero");
        }
        if (r.baseCurrencyCode() == null || !selected.contains(norm(r.baseCurrencyCode()))) errors.add("Base currency must be one of the selected currencies");
        if ("FOREX".equals(family) && selected.size() < 2) errors.add("Forex requires a funding and destination currency");
        validateAttributes(r, errors);
        validateEligibility(r, errors);
        validatePricing(r, family, selected, errors);
        validateCharges(r, family, selected, errors);
        validateRoles(r, errors);
        return errors.stream().distinct().toList();
    }

    private void validateAttributes(ConfigurationRequest r, List<String> errors) {
        Set<Integer> categories = lineage(r.categoryId(), categories());
        Map<Integer, String> supplied = r.attributes() == null ? Map.of() : r.attributes().stream().filter(a -> a.definitionId() != null).collect(java.util.stream.Collectors.toMap(AttributeValue::definitionId, a -> Objects.toString(a.value(), ""), (a, b) -> b));
        List<AttributeOption> defs = jdbc.query("SELECT ad_attribute_def_id,ad_category_id,ad_attribute_key,ad_data_type,ad_display_name,ad_is_required,ad_choice_values,ad_unit,ad_default_value,ad_min_value,ad_max_value,ad_display_order,ad_behavior_key FROM attribute_definitions",
                (rs, n) -> new AttributeOption(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6), split(rs.getString(7)), rs.getString(8), rs.getString(9), rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getInt(12), rs.getString(13), false)).stream().filter(a -> categories.contains(a.categoryId())).toList();
        for (AttributeOption def : defs) {
            String value = supplied.get(def.id());
            if (def.required() && (value == null || value.isBlank())) errors.add((def.displayName() == null ? def.key() : def.displayName()) + " is required");
            if (value == null || value.isBlank()) continue;
            try {
                if ("NUMBER".equals(def.dataType())) { BigDecimal n = new BigDecimal(value); if (def.minimumValue() != null && n.compareTo(def.minimumValue()) < 0) errors.add(def.key() + " is below its minimum"); if (def.maximumValue() != null && n.compareTo(def.maximumValue()) > 0) errors.add(def.key() + " exceeds its maximum"); }
                else if ("DATE".equals(def.dataType())) LocalDate.parse(value);
                else if ("BOOLEAN".equals(def.dataType()) && !Set.of("TRUE", "FALSE").contains(norm(value))) errors.add(def.key() + " must be true or false");
                if (!def.choices().isEmpty() && !def.choices().contains(norm(value))) errors.add(def.key() + " must be one of " + def.choices());
            } catch (RuntimeException e) { errors.add(def.key() + " has an invalid " + def.dataType().toLowerCase(Locale.ROOT) + " value"); }
        }
        for (Integer id : supplied.keySet()) if (defs.stream().noneMatch(d -> d.id() == id)) errors.add("Attribute definition does not belong to this product category: " + id);
    }

    private void validateEligibility(ConfigurationRequest r, List<String> errors) {
        if (r.eligibilityRules() == null) return;
        for (EligibilityRule rule : r.eligibilityRules()) {
            Fact fact = FACTS.get(norm(rule.fact()));
            if (fact == null) errors.add("Unrecognized eligibility fact: " + rule.fact());
            else if (!fact.executable()) errors.add("Unsupported eligibility fact " + rule.fact() + ": " + fact.dependency());
            else if (!fact.type().equals(norm(rule.dataType()))) errors.add("Eligibility fact data type does not match " + rule.fact());
            if (fact != null && !operators(fact.type()).contains(norm(rule.operator()))) errors.add("Unsupported eligibility operator: " + rule.operator());
            List<String> allowed = choices(norm(rule.fact()));
            if (!allowed.isEmpty()) {
                List<String> values = "IN".equals(norm(rule.operator())) ? split(rule.valueSet()) : List.of(Objects.toString(rule.valueFrom(),""));
                if (values.isEmpty() || values.stream().anyMatch(value -> allowed.stream().noneMatch(choice -> choice.equalsIgnoreCase(value.trim())))) errors.add("Choose a valid value for the selected eligibility fact.");
            }
            if (!Set.of("AND", "OR").contains(norm(orDefault(rule.combineLogic(), "AND")))) errors.add("Rule combination must be AND or OR");
        }
    }

    private void validatePricing(ConfigurationRequest r, String family, Set<String> selected, List<String> errors) {
        FamilyPricing expected = pricing(family);
        if (expected == null || r.pricingBands() == null || r.pricingBands().isEmpty()) { if (expected != null) errors.add("Each applicable currency requires pricing bands"); return; }
        Map<String,List<PricingBand>> byCode = new HashMap<>();
        for (PricingBand b : r.pricingBands()) {
            String code=norm(b.currencyCode()); if(!selected.contains(code)) errors.add("Pricing currency is not selected: " + code);
            if(!expected.fact().equals(norm(b.fact()))||!expected.outcome().equals(norm(b.outcomeType()))||!expected.method().equals(norm(b.calculationMethod()))) errors.add("Unsupported pricing behavior for " + family);
            if(!"DECIMAL_FRACTION".equals(norm(b.rateUnit()))||b.rate()==null||b.rate().signum()<0||b.rate().compareTo(BigDecimal.ONE)>0||b.rate().scale()>8) errors.add("Rates and markups use DECIMAL_FRACTION between 0 and 1 with at most 8 decimals");
            if(b.valueFrom()==null||b.valueTo()==null||b.valueFrom().signum()<0||b.valueFrom().compareTo(b.valueTo())>0) errors.add("Pricing band bounds are invalid for " + code);
            byCode.computeIfAbsent(code,k->new ArrayList<>()).add(b);
        }
        Set<String> needed = "FOREX".equals(family)?selected.stream().filter(c->!c.equals(norm(r.baseCurrencyCode()))).collect(java.util.stream.Collectors.toSet()):selected;
        if(!byCode.keySet().containsAll(needed)) errors.add("Each applicable currency requires pricing bands");
        for(var entry:byCode.entrySet()){
            List<PricingBand> bands=entry.getValue().stream().sorted(Comparator.comparing(PricingBand::valueFrom)).toList();
            BigDecimal step="TENURE_MONTHS".equals(expected.fact())?BigDecimal.ONE:BigDecimal.ONE.movePointLeft(scale("FOREX".equals(family)?r.baseCurrencyCode():entry.getKey()));
            for(int i=1;i<bands.size();i++){int cmp=bands.get(i).valueFrom().compareTo(bands.get(i-1).valueTo().add(step));if(cmp<0)errors.add("Pricing bands overlap or are ambiguous for " + entry.getKey());if(cmp>0)errors.add("Pricing bands leave an unsupported coverage gap for " + entry.getKey());}
        }
    }

    private void validateCharges(ConfigurationRequest r,String family,Set<String> selected,List<String> errors){
        if(r.charges()==null)return;
        for(Charge c:r.charges()){
            if(!Set.of("FIXED","PERCENTAGE").contains(norm(c.calculationBasis())))errors.add("Charge basis must be FIXED or PERCENTAGE");
            for(ChargeSlab s:c.slabs()){
                if(s.valueFrom().compareTo(s.valueTo())>0)errors.add("Charge slab minimum exceeds maximum");
                if(!Set.of("FIXED_AMOUNT","DECIMAL_FRACTION").contains(norm(s.unit())))errors.add("Charge unit must be FIXED_AMOUNT or DECIMAL_FRACTION");
                if("DECIMAL_FRACTION".equals(norm(s.unit()))&&s.value().compareTo(BigDecimal.ONE)>0)errors.add("Percentage charges use decimal fractions");
                if(s.floor()!=null&&s.cap()!=null&&s.floor().compareTo(s.cap())>0)errors.add("Charge floor must not exceed cap");
                if(!selected.contains(norm(s.currencyCode())))errors.add("Charge currency is not selected: " + s.currencyCode());
                if("FOREX".equals(family)&&!norm(s.currencyCode()).equals(norm(r.baseCurrencyCode())))errors.add("Forex charges must use the funding/base currency; fee-currency conversion is unsupported");
                if("FOREX".equals(family)&&!Set.of("TRANSFER_AMOUNT","FUNDING_AMOUNT").contains(norm(s.fact())))errors.add("Forex charges must use TRANSFER_AMOUNT or FUNDING_AMOUNT as their calculation fact");
            }
        }
    }

    private void validateRoles(ConfigurationRequest r,List<String> errors){
        Integer primary = jdbc.query("SELECT rot_role_type_id FROM role_type WHERE rot_role_code='PRIMARY_HOLDER'", rs -> rs.next() ? rs.getInt(1) : null);
        if (r.roleConstraints() == null || r.roleConstraints().stream().noneMatch(role -> Objects.equals(primary, role.roleTypeId()) && role.mandatory() && role.minimumCount() != null && role.minimumCount() >= 1))
            errors.add("A mandatory PRIMARY_HOLDER with minimum count of at least one is required");
        if(r.roleConstraints()==null)return;Set<Integer> seen=new HashSet<>();
        for(RoleConstraint role:r.roleConstraints()){
            if(!seen.add(role.roleTypeId()))errors.add("Role constraint appears more than once");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM role_type WHERE rot_role_type_id=?",Integer.class,role.roleTypeId())==0)errors.add("Role type not found: " + role.roleTypeId());
            if(role.minimumCount()!=null&&role.maximumCount()!=null&&role.minimumCount()>role.maximumCount())errors.add("Role minimum count exceeds maximum count");
            if(role.mandatory()&&(role.minimumCount()==null||role.minimumCount()<1))errors.add("Mandatory roles require a minimum count of at least one");
        }
    }

    private PricingPreview previewPricing(ConfigurationRequest r,Map<String,String> facts,String family){
        if("FOREX".equals(family))return forexPreview(r,facts);
        String code=norm(facts.getOrDefault("CURRENCY",r.baseCurrencyCode()));int scale=scale(code);FamilyPricing p=pricing(family);
        BigDecimal basis=decimalFact(facts,p.fact());PricingBand band=r.pricingBands().stream().filter(b->code.equals(norm(b.currencyCode()))&&basis.compareTo(b.valueFrom())>=0&&basis.compareTo(b.valueTo())<=0).findFirst().orElseThrow(()->bad("No pricing band covers the sample input"));
        BigDecimal amount=decimalFact(facts,"AMOUNT");Map<String,BigDecimal> values=new LinkedHashMap<>();values.put("eligible",BigDecimal.ONE);values.put("rateDecimalFraction",band.rate());
        if("LOAN".equals(family)){int months=integerFact(facts,"TENURE_MONTHS");BigDecimal mr=band.rate().divide(BigDecimal.valueOf(12),MathContext.DECIMAL128);BigDecimal f=BigDecimal.ONE.add(mr).pow(months,MathContext.DECIMAL128);BigDecimal emi=mr.signum()==0?amount.divide(BigDecimal.valueOf(months),MathContext.DECIMAL128):amount.multiply(mr).multiply(f).divide(f.subtract(BigDecimal.ONE),MathContext.DECIMAL128);values.put("monthlyInstallment",emi.setScale(scale,RoundingMode.HALF_UP));values.put("totalRepayment",emi.multiply(BigDecimal.valueOf(months)).setScale(scale,RoundingMode.HALF_UP));}
        else if("CASA".equals(family)){values.put("thirtyDayCreditInterest",amount.multiply(band.rate()).multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(scale,RoundingMode.HALF_UP));}
        else if("FIXED_DEPOSIT".equals(family)){
            int months=integerFact(facts,"TENURE_MONTHS");String basisName=norm(attributeValue(r,"COMPOUNDING_BASIS"));
            int period=switch(basisName){case "MONTHLY"->1;case "YEARLY"->12;default->3;};
            LocalDate start=LocalDate.now(),end=start.plusMonths(months);BigDecimal balance=amount,pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
            LocalDate next=start.plusMonths(period);int boundary=period;
            for(LocalDate day=start.plusDays(1);!day.isAfter(end);day=day.plusDays(1)){
                pending=pending.add(balance.multiply(band.rate()).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(8,RoundingMode.HALF_UP));
                if(day.equals(next)||day.equals(end)){balance=balance.add(pending.setScale(scale,RoundingMode.HALF_UP));pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);boundary+=period;next=start.plusMonths(boundary);}
            }
            values.put("interest",balance.subtract(amount).setScale(scale,RoundingMode.HALF_UP));values.put("maturityAmount",balance.setScale(scale,RoundingMode.HALF_UP));
        }
        values.put("charges",calculateCharges(r,amount,code,scale));return new PricingPreview("SUPPORTED",family,code,code,null,"FIXED_DEPOSIT".equals(family)?"FD_"+norm(orDefault(attributeValue(r,"COMPOUNDING_BASIS"),"QUARTERLY")):p.method(),values,List.of());
    }

    private PricingPreview forexPreview(ConfigurationRequest r,Map<String,String> facts){
        String funding=requiredCode(facts,"FUNDING_CURRENCY"),destination=requiredCode(facts,"DESTINATION_CURRENCY"),direction=requiredCode(facts,"REFERENCE_RATE_DIRECTION"),mode=requiredCode(facts,"FOREX_AMOUNT_MODE");
        if(!funding.equals(norm(r.baseCurrencyCode())))throw bad("Forex funding currency must equal the configured base currency");if(funding.equals(destination))throw bad("Forex destination currency must differ from the funding currency");
        if(r.currencies().stream().noneMatch(c->destination.equals(norm(c.currencyCode()))))throw bad("Forex destination currency is not configured");if(!"FUNDING_PER_DESTINATION".equals(direction))throw bad("Only FUNDING_PER_DESTINATION reference rates are supported");if(!Set.of("CONVERSION_PRINCIPAL","TOTAL_BUDGET").contains(mode))throw bad("FOREX_AMOUNT_MODE must be CONVERSION_PRINCIPAL or TOTAL_BUDGET");
        BigDecimal amount=decimalFact(facts,"TRANSFER_AMOUNT"),reference=decimalFact(facts,"REFERENCE_RATE");if(amount.signum()<=0||reference.signum()<=0)throw bad("Transfer amount and reference rate must be greater than zero");
        PricingBand band=r.pricingBands().stream().filter(b->destination.equals(norm(b.currencyCode()))&&amount.compareTo(b.valueFrom())>=0&&amount.compareTo(b.valueTo())<=0).findFirst().orElseThrow(()->bad("No Forex pricing band covers the funding amount"));
        int fs=scale(funding),ds=scale(destination);BigDecimal fees=calculateCharges(r,amount,funding,fs);BigDecimal principal="TOTAL_BUDGET".equals(mode)?amount.subtract(fees):amount;if(principal.signum()<=0)throw bad("Forex fees must be less than the total funding budget");BigDecimal total="TOTAL_BUDGET".equals(mode)?amount:principal.add(fees);BigDecimal rate=reference.multiply(BigDecimal.ONE.add(band.rate()),MathContext.DECIMAL128).setScale(12,RoundingMode.HALF_UP);BigDecimal converted=principal.divide(rate,ds,RoundingMode.HALF_UP);
        Map<String,BigDecimal> values=new LinkedHashMap<>();values.put("eligible",BigDecimal.ONE);values.put("markupRateDecimalFraction",band.rate());values.put("referenceRateFundingPerDestination",reference);values.put("customerRateFundingPerDestination",rate);values.put("conversionPrincipal",principal.setScale(fs,RoundingMode.HALF_UP));values.put("fundingCurrencyFees",fees);values.put("totalFundingDebit",total.setScale(fs,RoundingMode.HALF_UP));values.put("destinationAmount",converted);
        return new PricingPreview("SUPPORTED","FOREX",destination,funding,destination,"FOREX_REFERENCE_MARKUP",values,List.of("Reference rate direction is "+funding+" per one "+destination+" unit","The supplied reference rate is for preview only and is not a live quote"));
    }

    private BigDecimal calculateCharges(ConfigurationRequest r,BigDecimal amount,String code,int scale){BigDecimal total=BigDecimal.ZERO;if(r.charges()!=null)for(Charge c:r.charges()){ChargeSlab slab=c.slabs().stream().filter(s->code.equals(norm(s.currencyCode()))&&amount.compareTo(s.valueFrom())>=0&&amount.compareTo(s.valueTo())<=0).findFirst().orElse(null);if(slab!=null){BigDecimal v="DECIMAL_FRACTION".equals(norm(slab.unit()))?amount.multiply(slab.value()):slab.value();if(slab.floor()!=null&&v.compareTo(slab.floor())<0)v=slab.floor();if(slab.cap()!=null&&v.compareTo(slab.cap())>0)v=slab.cap();total=total.add(v);}}return total.setScale(scale,RoundingMode.HALF_UP);}

    private Integer insertProduct(ConfigurationRequest r) {
        boolean generated = r.productCode()==null || r.productCode().isBlank();
        String category = jdbc.queryForObject("SELECT pc_category_code FROM product_category WHERE pc_category_id=? FOR UPDATE",String.class,r.categoryId());
        String prefix = ProductTextRules.codePrefix(category,"PRODUCT",27);
        for (int attempt=0; attempt<5; attempt++) {
            String code = generated ? nextProductCode(prefix) : r.productCode();
            try {
                jdbc.update("INSERT INTO product(pro_product_code,pro_product_name,pro_category_id,pro_base_currency_code,pro_status,pro_effective_from,pro_effective_to,pro_description,pro_created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                        code, ProductTextRules.name(r.productName()), r.categoryId(), norm(r.baseCurrencyCode()), "DRAFT", date(r.effectiveFrom()), date(r.effectiveTo()), r.description()==null?null:r.description().trim(), "PRODUCT_BUILDER");
                return jdbc.queryForObject("SELECT LAST_INSERT_ID()",Integer.class);
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                if (!generated) throw bad("Product code is already in use.");
            }
        }
        throw bad("Could not assign a product code. Please retry.");
    }

    private String nextProductCode(String prefix) {
        String start=prefix+"_";
        List<String> codes=jdbc.queryForList("SELECT pro_product_code FROM product WHERE LEFT(pro_product_code,?)=? FOR UPDATE",String.class,start.length(),start);
        long highest=0;
        for (String code:codes) {
            String suffix=code.substring(start.length());
            if (suffix.matches("[0-9]+")) {
                try { highest=Math.max(highest,Long.parseLong(suffix)); }
                catch (NumberFormatException ignored) { throw bad("Product code sequence is exhausted."); }
            }
        }
        String suffix=String.format(Locale.ROOT,"%04d",highest+1);
        if (start.length()+suffix.length()>32 || highest==Long.MAX_VALUE) throw bad("Product code sequence is exhausted.");
        return start+suffix;
    }

    private void saveConfiguration(int productId, ConfigurationRequest r) {
        if (r.currencies() != null) for (CurrencyConfig c : r.currencies()) {
            Integer currencyId = jdbc.queryForObject("SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=?", Integer.class, norm(c.currencyCode()));
            jdbc.update("INSERT INTO product_currency_config(pcc_product_id,pcc_currency_id,pcc_is_base_currency,pcc_wallet_enabled,pcc_minimum_amount,pcc_maximum_amount,pcc_created_by) VALUES(?,?,?,?,?,?,?)",
                    productId, currencyId, norm(c.currencyCode()).equals(norm(r.baseCurrencyCode())), false, c.minimumAmount(), c.maximumAmount(), "PRODUCT_BUILDER");
        }
        if (r.attributes() != null) for (AttributeValue a : r.attributes()) {
            if (a.definitionId() == null || a.value() == null || a.value().isBlank()) continue;
            String type = jdbc.queryForObject("SELECT ad_data_type FROM attribute_definitions WHERE ad_attribute_def_id=?", String.class, a.definitionId());
            String value = a.value().trim();
            Object text = "STRING".equalsIgnoreCase(type) || "BOOLEAN".equalsIgnoreCase(type) ? value : null;
            Object number = "NUMBER".equalsIgnoreCase(type) ? new BigDecimal(value) : null;
            Object dateValue = "DATE".equalsIgnoreCase(type) ? java.sql.Timestamp.valueOf(LocalDate.parse(value).atStartOfDay()) : null;
            jdbc.update("INSERT INTO product_attribute_values(pav_product_id,pav_attribute_def_id,pav_value_string,pav_value_number,pav_value_date,pav_created_by) VALUES(?,?,?,?,?,?)",
                    productId, a.definitionId(), text, number, dateValue, "PRODUCT_BUILDER");
        }
        if (r.eligibilityRules() != null) for (EligibilityRule rule : r.eligibilityRules()) {
            long id = insertRule(productId, "ELIGIBILITY", rule.name(), "AND");
            jdbc.update("INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_value_set,rc_created_by) VALUES(?,?,?,?,?,?,?)",
                    id, norm(rule.fact()), norm(rule.operator()), rule.valueFrom(), rule.valueTo(), rule.valueSet(), "PRODUCT_BUILDER");
        }
        if (r.pricingBands() != null) for (PricingBand band : r.pricingBands()) {
            long id = insertRule(productId, "PRICING_BAND", "Pricing " + band.currencyCode() + " " + band.valueFrom() + "-" + band.valueTo(), "AND");
            jdbc.update("INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_created_by) VALUES(?,?,?,?,?,?)",
                    id, norm(band.fact()), "BETWEEN_INCLUSIVE", band.valueFrom().toPlainString(), band.valueTo().toPlainString(), "PRODUCT_BUILDER");
            Integer currencyId = jdbc.queryForObject("SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=?", Integer.class, norm(band.currencyCode()));
            jdbc.update("INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_compounding_basis,ro_payout_frequency,ro_currency_id,ro_created_by) VALUES(?,?,?,?,?,?,?,?)",
                    id, norm(band.outcomeType()), band.rate(), norm(band.rateUnit()), attributeValue(r, "COMPOUNDING_BASIS"), attributeValue(r, "PAYOUT_FREQUENCY"), currencyId, "PRODUCT_BUILDER");
        }
        if (r.charges() != null) for (Charge charge : r.charges()) {
            jdbc.update("INSERT INTO charge_config(cc_product_id,cc_charge_code,cc_calculation_basis,cc_is_active,cc_created_by) VALUES(?,?,?,?,?)",
                    productId, norm(charge.chargeCode()), norm(charge.calculationBasis()), true, "PRODUCT_BUILDER");
            long chargeId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            for (ChargeSlab slab : charge.slabs()) {
                Integer currencyId = jdbc.queryForObject("SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=?", Integer.class, norm(slab.currencyCode()));
                jdbc.update("INSERT INTO charge_slab(cs_charge_id,cs_slab_attribute,cs_value_from,cs_value_to,cs_charge_amount,cs_currency_id,cs_charge_unit,cs_minimum_charge,cs_maximum_charge,cs_created_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        chargeId, norm(slab.fact()), slab.valueFrom(), slab.valueTo(), slab.value(), currencyId, norm(slab.unit()), slab.floor(), slab.cap(), "PRODUCT_BUILDER");
            }
        }
        if (r.roleConstraints() != null) for (RoleConstraint role : r.roleConstraints()) {
            jdbc.update("INSERT INTO product_role_config(prc_product_id,prc_role_type_id,prc_is_mandatory,prc_min_count,prc_max_count,prc_created_by) VALUES(?,?,?,?,?,?)",
                    productId, role.roleTypeId(), role.mandatory(), role.minimumCount(), role.maximumCount(), "PRODUCT_BUILDER");
        }
        FdPrematurePenaltyDefaults.ensureForProduct(jdbc, new FixedDepositCategories(jdbc), productId);
    }

    private long insertRule(int productId, String type, String name, String combine) {
        Integer typeId = jdbc.queryForObject("SELECT rut_rule_type_id FROM rule_type WHERE UPPER(rut_rule_type_code)=?", Integer.class, type);
        jdbc.update("INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_created_by) VALUES(?,?,?,?,?,?,?)",
                productId, typeId, name, 1, combine, true, "PRODUCT_BUILDER");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String attributeValue(ConfigurationRequest r, String key) {
        if (r.attributes() == null) return null;
        return r.attributes().stream().filter(a -> a.definitionId() != null)
                .filter(a -> key.equals(jdbc.query("SELECT ad_attribute_key FROM attribute_definitions WHERE ad_attribute_def_id=?", rs -> rs.next() ? rs.getString(1) : null, a.definitionId())))
                .map(AttributeValue::value).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private void saveRevision(int productId, String status, ConfigurationRequest request) {
        int revision = jdbc.queryForObject("SELECT COALESCE(MAX(pcr_revision_no),0)+1 FROM product_configuration_revision WHERE pcr_product_id=?", Integer.class, productId);
        try {
            String snapshot = json.writeValueAsString(request);
            jdbc.update("INSERT INTO product_configuration_revision(pcr_product_id,pcr_revision_no,pcr_status,pcr_snapshot_json,pcr_created_by) VALUES(?,?,?,?,?)",
                    productId, revision, status, snapshot, "PRODUCT_BUILDER");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Could not save product configuration revision", e); }
    }

    private ConfigurationRequest readSnapshot(String snapshot) {
        try { return json.readValue(snapshot, ConfigurationRequest.class); }
        catch (java.io.IOException e) { throw new IllegalStateException("Could not read product configuration revision", e); }
    }

    private ConfigurationSummary findSummary(int id) {
        List<Category> cats = categories();
        return jdbc.query("SELECT p.pro_product_id,p.pro_product_code,p.pro_product_name,p.pro_category_id,p.pro_status,p.pro_base_currency_code,(SELECT COUNT(*) FROM product_configuration_revision r WHERE r.pcr_product_id=p.pro_product_id),(SELECT GROUP_CONCAT(c.cur_iso_code ORDER BY c.cur_iso_code) FROM product_currency_config x JOIN currency c ON c.cur_currency_id=x.pcc_currency_id WHERE x.pcc_product_id=p.pro_product_id) FROM product p WHERE p.pro_product_id=?",
                rs -> rs.next() ? summary(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getString(8),cats) : null, id);
    }

    private ConfigurationSummary summary(int id,String code,String name,int categoryId,String status,String base,int revision,String codes,List<Category> cats) {
        Category c = cats.stream().filter(x -> x.id() == categoryId).findFirst().orElseThrow(() -> bad("Product category not found"));
        String family = family(categoryId, cats);
        List<String> currencies = codes == null || codes.isBlank() ? List.of() : Arrays.asList(codes.split(","));
        return new ConfigurationSummary(id,"FIXED_DEPOSIT".equals(family)?(long)id:null,code,name,family,c.code(),status,base,currencies,revision);
    }

    private void writeOutbox(int id) {
        FdPrematurePenaltyDefaults.ensureForProduct(jdbc, new FixedDepositCategories(jdbc), id);
        Map<String,Object> product = jdbc.queryForMap("SELECT pro_product_code,pro_product_name,pro_description,pro_base_currency_code,pro_status,pro_event_version FROM product WHERE pro_product_id=?", id);
        List<Map<String,Object>> currencies = jdbc.queryForList("SELECT c.cur_currency_id,c.cur_iso_code,c.cur_currency_name,c.cur_decimal_places,p.pcc_minimum_amount,p.pcc_maximum_amount,p.pcc_is_base_currency,(SELECT o.ro_outcome_value FROM product_rule pr JOIN rule_outcome o ON o.ro_rule_id=pr.pr_rule_id WHERE pr.pr_product_id=p.pcc_product_id AND o.ro_currency_id=p.pcc_currency_id AND o.ro_outcome_type='CLOSURE_PENALTY' AND pr.pr_is_active=1 ORDER BY pr.pr_priority LIMIT 1) premature_penalty_points FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? ORDER BY c.cur_iso_code", id);
        List<Map<String,Object>> rates = jdbc.queryForList("SELECT o.ro_outcome_value,o.ro_currency_id,o.ro_compounding_basis,o.ro_payout_frequency,c.cur_iso_code,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_from END) min_month,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_to END) max_month,DATE_FORMAT(COALESCE(DATE(r.pr_effective_from),DATE(p.pro_effective_from)),'%Y-%m-%d') effectiveFrom,DATE_FORMAT(COALESCE(DATE(r.pr_effective_to),DATE(p.pro_effective_to)),'%Y-%m-%d') effectiveTo FROM product_rule r JOIN product p ON p.pro_product_id=r.pr_product_id JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id JOIN currency c ON c.cur_currency_id=o.ro_currency_id JOIN rule_condition q ON q.rc_rule_id=r.pr_rule_id WHERE r.pr_product_id=? AND r.pr_is_active=1 AND o.ro_outcome_type='INTEREST_RATE' GROUP BY r.pr_rule_id,o.ro_outcome_value,o.ro_currency_id,o.ro_compounding_basis,o.ro_payout_frequency,c.cur_iso_code,r.pr_effective_from,r.pr_effective_to,p.pro_effective_from,p.pro_effective_to ORDER BY c.cur_iso_code,min_month", id);
        Map<String,Object> payload = Map.of("eventId",UUID.randomUUID().toString(),"eventType","PRODUCT_CATALOG","schemaVersion",1,"occurredAt",java.time.Instant.now().toString(),"aggregateId",Integer.toString(id),"sourceVersion",product.get("pro_event_version"),"product",product,"currencies",currencies,"rateBands",rates);
        try { jdbc.update("INSERT INTO outbox_event(event_key,event_type,topic,source_version,payload) VALUES(?,?,?,?,CAST(? AS JSON))", Integer.toString(id),"PRODUCT_CATALOG","product.catalog",product.get("pro_event_version"),json.writeValueAsString(payload)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Unable to serialize product snapshot",e); }
    }

    private List<Category> categories() { return jdbc.query("SELECT pc_category_id,pc_category_code,pc_category_name,pc_parent_category_id FROM product_category", (r,n)->new Category(r.getInt(1),r.getString(2),r.getString(3),r.getObject(4)==null?null:((Number)r.getObject(4)).intValue())); }
    private String safeFamily(Integer id) { if(id==null)return "UNKNOWN"; return family(id,categories()); }
    private String family(int id,List<Category> cats) { Category c=cats.stream().filter(x->x.id()==id).findFirst().orElse(null); if(c==null)return "UNKNOWN"; if(FAMILIES.contains(norm(c.code())))return norm(c.code()); return c.parentId()==null?"UNKNOWN":family(c.parentId(),cats); }
    private Set<Integer> lineage(int id,List<Category> cats) { Set<Integer> values=new HashSet<>(); Category c=cats.stream().filter(x->x.id()==id).findFirst().orElse(null); while(c!=null&&values.add(c.id())) { Integer parent=c.parentId(); c=parent==null?null:cats.stream().filter(x->x.id()==parent).findFirst().orElse(null); } return values; }
    private int scale(String code) { Integer value=jdbc.query("SELECT cur_decimal_places FROM currency WHERE UPPER(cur_iso_code)=? AND cur_is_active=1",rs->rs.next()?rs.getInt(1):null,norm(code)); if(value==null)throw bad("Currency is not active: "+code); return value; }
    private boolean matches(EligibilityRule rule,String raw) { String actual=Objects.toString(raw,""); String expected=Objects.toString(rule.valueFrom(),""); int cmp; if("NUMBER".equals(norm(rule.dataType()))) { try{cmp=new BigDecimal(actual).compareTo(new BigDecimal(expected));}catch(Exception e){return false;} } else cmp=actual.compareToIgnoreCase(expected); return switch(norm(rule.operator())) { case "EQ"->cmp==0; case "NE"->cmp!=0; case "GT"->cmp>0; case "GTE"->cmp>=0; case "LT"->cmp<0; case "LTE"->cmp<=0; case "BETWEEN_INCLUSIVE"->{try{yield new BigDecimal(actual).compareTo(new BigDecimal(rule.valueFrom()))>=0&&new BigDecimal(actual).compareTo(new BigDecimal(rule.valueTo()))<=0;}catch(Exception e){yield false;}} case "IN"->Arrays.stream(Objects.toString(rule.valueSet(),"").split(",")).map(String::trim).anyMatch(v->v.equalsIgnoreCase(actual)); default->false;}; }
    private BigDecimal decimalFact(Map<String,String> facts,String key) { try{return new BigDecimal(Objects.requireNonNull(facts.get(key),"Missing fact: "+key));}catch(RuntimeException e){throw bad("Missing or invalid numeric fact: "+key);} }
    private int integerFact(Map<String,String> facts,String key) { try{return decimalFact(facts,key).intValueExact();}catch(RuntimeException e){throw bad("Missing or invalid integer fact: "+key);} }
    private String requiredCode(Map<String,String> facts,String key) { String v=facts.get(key); if(v==null||v.isBlank())throw bad("Missing fact: "+key); return norm(v); }
    private String date(LocalDate date) { return date==null?null:date.toString(); }
    private List<String> split(String raw) { return raw==null||raw.isBlank()?List.of():Arrays.stream(raw.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList(); }
    private List<String> operators(String type) { if ("BOOLEAN".equals(type)) return List.of("EQ","NE"); return "NUMBER".equals(type)?List.of("EQ","NE","GT","GTE","LT","LTE","BETWEEN_INCLUSIVE","IN"):List.of("EQ","NE","IN"); }
    private List<String> choices(String fact) { return switch(fact) { case "KYC_STATUS" -> List.of("PENDING","VERIFIED","REJECTED"); case "RISK_CATEGORY" -> List.of("LOW","MEDIUM","HIGH"); case "VERIFIED_IDENTITY" -> List.of("true","false"); default -> List.of(); }; }
    private String norm(String value) { return value==null?"":value.trim().toUpperCase(Locale.ROOT); }
    private String orDefault(String value,String fallback) { return value==null||value.isBlank()?fallback:value; }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }

    public record IdentityRequest(String productName,String description,LocalDate effectiveFrom,LocalDate effectiveTo) {}
    public record Category(int id,String code,String name,Integer parentId) {}
    public record CategoryOption(int id,String code,String name,Integer parentId,String family) {}
    public record CurrencyOption(int id,String code,String name,String symbol,int decimalPlaces) {}
    public record AttributeOption(int id,int categoryId,String key,String dataType,String displayName,boolean required,List<String> choices,String unit,String defaultValue,BigDecimal minimumValue,BigDecimal maximumValue,int displayOrder,String behaviorKey,boolean inherited) {}
    public record RoleOption(int id,String code,String description) {}
    public record FactOption(String code,String dataType,List<String> operators,List<String> choices,boolean executable,String dependency) {}
    public record BuilderMetadata(List<CategoryOption> categories,List<CurrencyOption> currencies,List<AttributeOption> attributes,List<RoleOption> roles,List<FactOption> facts,List<String> operators,List<String> outcomeTypes,List<String> calculationMethods) {}
    public record ConfigurationRequest(@Size(max=32) String productCode,@NotBlank @Size(max=255) String productName,@NotNull @Min(1) Integer categoryId,@Size(max=1000) String description,LocalDate effectiveFrom,LocalDate effectiveTo,@NotBlank @Size(max=3) String baseCurrencyCode,@NotEmpty @Valid List<CurrencyConfig> currencies,@Valid List<AttributeValue> attributes,@Valid List<EligibilityRule> eligibilityRules,@NotEmpty @Valid List<PricingBand> pricingBands,@Valid List<Charge> charges,@Valid List<RoleConstraint> roleConstraints) {}
    public record CurrencyConfig(@NotBlank @Size(max=3) String currencyCode,@NotNull @DecimalMin("0.00") BigDecimal minimumAmount,@NotNull @DecimalMin("0.01") BigDecimal maximumAmount) {}
    public record AttributeValue(@NotNull @Min(1) Integer definitionId,String value) {}
    public record EligibilityRule(@NotBlank @Size(max=150) String name,@NotBlank String fact,@NotBlank String dataType,@NotBlank String operator,String valueFrom,String valueTo,String valueSet,String combineLogic) {}
    public record PricingBand(@NotBlank @Size(max=3) String currencyCode,@NotBlank String fact,@NotNull BigDecimal valueFrom,@NotNull BigDecimal valueTo,@NotNull @DecimalMin("0.0") BigDecimal rate,@NotBlank String outcomeType,@NotBlank String rateUnit,@NotBlank String calculationMethod) {}
    public record Charge(@NotBlank @Size(max=50) String chargeCode,@NotBlank String calculationBasis,@Valid @NotEmpty List<ChargeSlab> slabs) {}
    public record ChargeSlab(@NotBlank String fact,@NotNull BigDecimal valueFrom,@NotNull BigDecimal valueTo,@NotNull @DecimalMin("0.0") BigDecimal value,@NotBlank String unit,@NotBlank @Size(max=3) String currencyCode,BigDecimal floor,BigDecimal cap) {}
    public record RoleConstraint(@NotNull @Min(1) Integer roleTypeId,boolean mandatory,@Min(0) Integer minimumCount,@Min(0) Integer maximumCount) {}
    public record PreviewRequest(@NotNull @Valid ConfigurationRequest configuration,Map<String,String> facts) {}
    public record ValidationResult(boolean valid,List<String> errors) {}
    public record PricingPreview(String status,String family,String currencyCode,String fundingCurrencyCode,String destinationCurrencyCode,String calculationMethod,Map<String,BigDecimal> values,List<String> messages) {}
    public record ConfigurationSummary(Integer productId,Long fdProductId,String productCode,String productName,String family,String categoryCode,String status,String baseCurrencyCode,List<String> currencyCodes,Integer revision) {}
    private record Fact(String type,boolean executable,String dependency) {}
    private record FamilyPricing(String fact,String outcome,String method) {}
    private FamilyPricing pricing(String family) { return switch(family) { case "LOAN"->new FamilyPricing("TENURE_MONTHS","INTEREST_RATE","LOAN_REDUCING_BALANCE"); case "CASA"->new FamilyPricing("BALANCE_AMOUNT","CREDIT_INTEREST_RATE","CASA_DAILY_BALANCE"); case "FIXED_DEPOSIT"->new FamilyPricing("TENURE_MONTHS","INTEREST_RATE","FD_QUARTERLY"); case "FOREX"->new FamilyPricing("TRANSFER_AMOUNT","MARKUP_RATE","FOREX_REFERENCE_MARKUP"); default->null; }; }
}
