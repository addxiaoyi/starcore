package dev.starcore.starcore.module.technology;

import dev.starcore.starcore.module.nation.model.NationId;
import dev.starcore.starcore.module.technology.model.TechnologyDefinition;
import dev.starcore.starcore.module.technology.model.TechnologyEffect;
import dev.starcore.starcore.module.treasury.TreasuryService;
import dev.starcore.starcore.module.resource.ResourceService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Validates technology requirements including prerequisites, mutual exclusivity,
 * and cost affordability.
 */
public final class TechnologyValidator {
    private final TechnologyService technologyService;
    private final Supplier<TreasuryService> treasuryServiceSupplier;
    private final Supplier<ResourceService> resourceServiceSupplier;
    private final TechnologyDefinitionLoader definitionLoader;

    /**
     * Result of a validation check with details about any failures.
     */
    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings,
        List<String> missingPrerequisites,
        List<String> conflictingTechnologies
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of(), List.of(), List.of());
        }

        public static ValidationResult failure(String error, String... moreErrors) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            for (String e : moreErrors) {
                errors.add(e);
            }
            return new ValidationResult(false, List.copyOf(errors), List.of(), List.of(), List.of());
        }

        public ValidationResult withError(String error) {
            List<String> newErrors = new ArrayList<>(errors);
            newErrors.add(error);
            return new ValidationResult(false, List.copyOf(newErrors), warnings, missingPrerequisites, conflictingTechnologies);
        }

        public ValidationResult withWarning(String warning) {
            List<String> newWarnings = new ArrayList<>(warnings);
            newWarnings.add(warning);
            return new ValidationResult(valid, errors, List.copyOf(newWarnings), missingPrerequisites, conflictingTechnologies);
        }
    }

    public TechnologyValidator(
            TechnologyService technologyService,
            Supplier<TreasuryService> treasuryServiceSupplier,
            Supplier<ResourceService> resourceServiceSupplier,
            TechnologyDefinitionLoader definitionLoader) {
        this.technologyService = technologyService;
        this.treasuryServiceSupplier = treasuryServiceSupplier;
        this.resourceServiceSupplier = resourceServiceSupplier;
        this.definitionLoader = definitionLoader;
    }

    /**
     * Validates that a technology can be researched/unlocked.
     * Checks: prerequisites, mutual exclusivity, already unlocked, and affordability.
     *
     * @param nationId The nation attempting to research
     * @param technologyKey The technology to validate
     * @return ValidationResult with success/failure and details
     */
    public ValidationResult validate(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        TechnologyDefinition definition = definitionLoader.load(normalized);

        if (definition == null) {
            return ValidationResult.failure("Unknown technology: " + technologyKey);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingPrereqs = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        // Check if already unlocked
        if (technologyService.hasTechnology(nationId, normalized)) {
            return ValidationResult.failure("Technology already unlocked: " + definition.displayName());
        }

        // Check prerequisites
        List<String> missing = checkPrerequisites(nationId, definition);
        if (!missing.isEmpty()) {
            missingPrereqs.addAll(missing);
            errors.add("Missing prerequisites: " + String.join(", ", missing));
        }

        // Check mutual exclusivity
        List<String> conflicting = checkMutualExclusivity(nationId, definition);
        if (!conflicting.isEmpty()) {
            conflicts.addAll(conflicting);
            errors.add("Conflicts with unlocked technologies: " + String.join(", ", conflicting));
        }

        // Check treasury cost
        BigDecimal treasuryCost = definition.treasuryCost();
        if (treasuryCost.signum() > 0) {
            TreasuryService treasuryService = treasuryServiceSupplier.get();
            if (treasuryService != null) {
                BigDecimal balance = treasuryService.balance(nationId);
                if (balance.compareTo(treasuryCost) < 0) {
                    // TODO i18n: use MessageService to format localized error
                    // e.g., messages.get("tech.error.insufficient_treasury", treasuryCost, balance)
                    errors.add("Insufficient treasury funds: need " + treasuryCost + ", have " + balance);
                }
            } else {
                errors.add("Treasury service unavailable, cannot verify treasury cost");
            }
        }

        // Check resource costs (from TechnologyCost)
        var costOpt = technologyService.costOf(normalized);
        if (costOpt.isPresent()) {
            TechnologyCost cost = costOpt.get();
            ResourceService resourceService = resourceServiceSupplier.get();
            if (resourceService != null) {
                for (Map.Entry<String, Long> entry : cost.resources().entrySet()) {
                    String resourceType = entry.getKey();
                    long required = entry.getValue();
                    long available = resourceService.amount(nationId, resourceType);
                    if (available < required) {
                        // TODO i18n: use MessageService to format localized error
                        errors.add("Insufficient " + resourceType + ": need " + required + ", have " + available);
                    }
                }
            } else {
                errors.add("Resource service unavailable, cannot verify resource costs");
            }
        }

        return new ValidationResult(
            errors.isEmpty(),
            List.copyOf(errors),
            List.copyOf(warnings),
            List.copyOf(missingPrereqs),
            List.copyOf(conflicts)
        );
    }

    /**
     * Quick check if a nation can afford to research a technology.
     * Only checks treasury and resources, not prerequisites.
     * Note: This is a non-atomic pre-check only. Between this check and actual
     * deduction (e.g., in startResearch), another process may consume resources,
     * causing a race condition where pre-check passes but actual deduction fails.
     * Callers must handle deduction failures gracefully.
     *
     * @param nationId The nation
     * @param technologyKey The technology
     * @return true if can afford, false otherwise
     */
    public boolean canAfford(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        TechnologyDefinition definition = definitionLoader.load(normalized);
        if (definition == null) {
            return false;
        }

        // Check treasury
        BigDecimal treasuryCost = definition.treasuryCost();
        if (treasuryCost.signum() > 0) {
            TreasuryService treasuryService = treasuryServiceSupplier.get();
            if (treasuryService != null) {
                if (treasuryService.balance(nationId).compareTo(treasuryCost) < 0) {
                    return false;
                }
            }
        }

        // Check resources
        var costOpt = technologyService.costOf(normalized);
        if (costOpt.isPresent()) {
            ResourceService resourceService = resourceServiceSupplier.get();
            if (resourceService != null) {
                for (Map.Entry<String, Long> entry : costOpt.get().resources().entrySet()) {
                    if (resourceService.amount(nationId, entry.getKey()) < entry.getValue()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Checks if all prerequisites are met for a technology.
     *
     * @param nationId The nation
     * @param definition The technology definition
     * @return List of missing prerequisite keys (empty if all met)
     */
    public List<String> checkPrerequisites(NationId nationId, TechnologyDefinition definition) {
        List<String> missing = new ArrayList<>();
        for (String prereq : definition.prerequisites()) {
            if (!technologyService.hasTechnology(nationId, prereq)) {
                TechnologyDefinition prereqDef = definitionLoader.load(prereq);
                String displayName = prereqDef != null ? prereqDef.displayName() : prereq;
                missing.add(displayName);
            }
        }
        return missing;
    }

    /**
     * Checks if a technology conflicts with any unlocked technologies.
     *
     * @param nationId The nation
     * @param definition The technology definition
     * @return List of conflicting unlocked technology keys (empty if no conflicts)
     */
    public List<String> checkMutualExclusivity(NationId nationId, TechnologyDefinition definition) {
        List<String> conflicts = new ArrayList<>();
        for (String exclusive : definition.mutuallyExclusive()) {
            if (technologyService.hasTechnology(nationId, exclusive)) {
                TechnologyDefinition conflictDef = definitionLoader.load(exclusive);
                String displayName = conflictDef != null ? conflictDef.displayName() : exclusive;
                conflicts.add(displayName);
            }
        }
        return conflicts;
    }

    /**
     * Gets all missing prerequisites for a technology, in order.
     *
     * @param nationId The nation
     * @param technologyKey The technology
     * @return Ordered list of missing prerequisites
     */
    public List<String> getMissingPrerequisitesInOrder(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        TechnologyDefinition definition = definitionLoader.load(normalized);
        if (definition == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        collectMissingPrerequisites(nationId, definition, result, new java.util.HashSet<>());
        return result;
    }

    private void collectMissingPrerequisites(NationId nationId, TechnologyDefinition definition,
                                              List<String> result, java.util.Set<String> visited) {
        for (String prereq : definition.prerequisites()) {
            if (visited.contains(prereq)) {
                continue; // Avoid cycles
            }
            visited.add(prereq);

            if (!technologyService.hasTechnology(nationId, prereq)) {
                TechnologyDefinition prereqDef = definitionLoader.load(prereq);
                if (prereqDef != null) {
                    // First collect prerequisites of this prerequisite
                    collectMissingPrerequisites(nationId, prereqDef, result, visited);
                    result.add(prereqDef.displayName());
                } else {
                    result.add(prereq);
                }
            }
        }
    }

    /**
     * Calculates the total cost for researching a technology (treasury + resources).
     *
     * @param nationId The nation
     * @param technologyKey The technology
     * @return Detailed cost breakdown
     */
    public CostBreakdown calculateTotalCost(NationId nationId, String technologyKey) {
        String normalized = normalizeKey(technologyKey);
        TechnologyDefinition definition = definitionLoader.load(normalized);
        if (definition == null) {
            return new CostBreakdown(BigDecimal.ZERO, Map.of(), Map.of(), Map.of());
        }

        BigDecimal treasuryCost = definition.treasuryCost();
        Map<String, Long> resourceCosts = Map.of();
        Map<String, Long> treasuryDeficit = Map.of();
        Map<String, Long> resourceDeficit = Map.of();

        // Get resource costs from TechnologyCost
        var costOpt = technologyService.costOf(normalized);
        if (costOpt.isPresent()) {
            resourceCosts = costOpt.get().resources();

            // Calculate deficits
            ResourceService resourceService = resourceServiceSupplier.get();
            if (resourceService != null) {
                java.util.Map<String, Long> deficits = new java.util.LinkedHashMap<>();
                for (Map.Entry<String, Long> entry : resourceCosts.entrySet()) {
                    long available = resourceService.amount(nationId, entry.getKey());
                    if (available < entry.getValue()) {
                        deficits.put(entry.getKey(), entry.getValue() - available);
                    }
                }
                if (!deficits.isEmpty()) {
                    resourceDeficit = Map.copyOf(deficits);
                }
            }
        }

        // Calculate treasury deficit
        TreasuryService treasuryService = treasuryServiceSupplier.get();
        if (treasuryService != null && treasuryCost.signum() > 0) {
            BigDecimal balance = treasuryService.balance(nationId);
            if (balance.compareTo(treasuryCost) < 0) {
                // Note: BigDecimal to long conversion may lose precision for extreme values
                // exceeding Long.MAX_VALUE. In practice, treasury amounts are bounded.
                BigDecimal deficit = treasuryCost.subtract(balance);
                treasuryDeficit = Map.of("treasury", deficit.longValue());
            }
        }

        return new CostBreakdown(treasuryCost, resourceCosts, treasuryDeficit, resourceDeficit);
    }

    /**
     * Detailed cost breakdown for a technology.
     */
    public record CostBreakdown(
        BigDecimal treasuryCost,
        Map<String, Long> resourceCosts,
        Map<String, Long> treasuryDeficit,
        Map<String, Long> resourceDeficit
    ) {
        public boolean hasDeficit() {
            return !treasuryDeficit.isEmpty() || !resourceDeficit.isEmpty();
        }

        public BigDecimal totalTreasuryDeficit() {
            if (treasuryDeficit.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(treasuryDeficit.get("treasury"));
        }
    }

    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
