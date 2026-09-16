package kr.hankkitravel.recommendation.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure, deterministic recommendation scoring. It only consumes request-scoped decision data and never makes
 * medical or ingredient-safety claims. Missing evidence is represented as NOT_EVALUATED, not as a zero score.
 */
public final class RecommendationCore {
    public static final String FAMILY_MEAL_FIT = "FAMILY_MEAL_FIT";
    public static final String TRIP_ROUTE_FIT = "TRIP_ROUTE_FIT";
    public static final String MOBILITY_FIT = "MOBILITY_FIT";
    public static final String AREA_DEMAND_SIGNAL = "AREA_DEMAND_SIGNAL";
    public static final String REVIEW_SIGNAL = "REVIEW_SIGNAL";
    public static final String LOCAL_MENU_FIT = "LOCAL_MENU_FIT";
    public static final String MEAL = FAMILY_MEAL_FIT;
    public static final String MOVEMENT = MOBILITY_FIT;
    public static final String WALKING = "WALKING_DETAIL";
    public static final String LOCAL_FOOD = LOCAL_MENU_FIT;
    public static final String ITINERARY = TRIP_ROUTE_FIT;
    public static final String PERSONAL = REVIEW_SIGNAL;
    private static final List<String> DIMENSIONS = List.of(FAMILY_MEAL_FIT, TRIP_ROUTE_FIT, MOBILITY_FIT,
            AREA_DEMAND_SIGNAL, REVIEW_SIGNAL, LOCAL_MENU_FIT);
    private static final Set<String> NUTRIENT_CAUTIONS = Set.of("SODIUM", "SUGAR", "CARBOHYDRATE");
    private static final List<String> SPICY_CUES = List.of("매운", "불닭", "마라", "짬뽕", "매콤", "핫", "spicy");
    private final RecommendationScoringProperties scoring;

    public RecommendationCore() { this(new RecommendationScoringProperties()); }
    public RecommendationCore(RecommendationScoringProperties scoring) { this.scoring = Objects.requireNonNull(scoring); }

    public List<ScoredCandidate> score(List<Candidate> candidates, FamilyContext family, RequestContext context) {
        Objects.requireNonNull(candidates);
        if (candidates.isEmpty()) return List.of();
        var nutrientScores = nutrientScores(candidates, family.cautions());
        return candidates.stream().map(candidate -> scoreCandidate(candidate, family, context,
                nutrientScores.getOrDefault(candidate.contentId(), Map.of()))).toList();
    }

    public HardDecision hardDecision(Candidate candidate, FamilyContext family, RequestContext context) {
        var reasons = new ArrayList<String>();
        String searchable = normalize(candidate.title() + " " + candidate.address() + " "
                + candidate.menus().stream().map(MenuEvidence::rawName).collect(Collectors.joining(" ")));
        var exclusions = new ArrayList<String>(context.strictExclusions());
        exclusions.addAll(family.allergens());
        exclusions.addAll(family.avoidedFoods());
        for (String exclusion : exclusions) {
            if (!exclusion.isBlank() && searchable.contains(normalize(exclusion))) {
                reasons.add("사용자가 지정한 엄격 제외조건과 공개 메뉴 정보가 겹쳐요.");
                return new HardDecision(false, reasons);
            }
        }
        if ("REQUIRED".equals(family.parkingPreference()) && explicitlyNoParking(candidate.parking())) {
            reasons.add("주차가 필수인데 공개 주차정보에서 이용이 어렵다고 안내돼요.");
            return new HardDecision(false, reasons);
        }
        if (context.maximumTransitMinutes() != null && candidate.transit() != null
                && candidate.transit().totalTimeMinutes().compareTo(context.maximumTransitMinutes()) > 0) {
            reasons.add("설정한 최대 이동시간을 넘는 경로예요.");
            return new HardDecision(false, reasons);
        }
        return new HardDecision(true, List.of());
    }

    public List<RankedCandidate> rank(List<ScoredCandidate> candidates, Perspective perspective) {
        return candidates.stream().map(candidate -> rank(candidate, perspective))
                .sorted(Comparator.comparingInt(RankedCandidate::compatibilityScore).reversed()
                        .thenComparing(Comparator.comparingInt(RankedCandidate::evidenceCoverage).reversed())
                        .thenComparing((RankedCandidate item) -> item.dimensionScore(MEAL), Comparator.reverseOrder())
                        .thenComparing((RankedCandidate item) -> item.dimensionScore(MOVEMENT), Comparator.reverseOrder())
                        .thenComparing(item -> item.candidate().candidate().contentId(), RecommendationCore::stableContentIdCompare))
                .limit(3).toList();
    }

    private RankedCandidate rank(ScoredCandidate candidate, Perspective perspective) {
        int evaluatedWeight = 0;
        double weighted = 0;
        for (String dimension : DIMENSIONS) {
            var value = candidate.dimensions().get(dimension);
            int weight = scoring.weight(dimension);
            if (weight > 0 && value.state() != EvidenceState.NOT_EVALUATED) {
                evaluatedWeight += weight;
                weighted += value.score() * weight;
            }
        }
        int compatibility = evaluatedWeight == 0 ? 0
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(evaluatedWeight), 0, RoundingMode.HALF_UP).intValue();
        return new RankedCandidate(candidate, perspective, compatibility, evaluatedWeight, evaluatedWeight);
    }

    private ScoredCandidate scoreCandidate(Candidate candidate, FamilyContext family, RequestContext context,
            Map<String, Integer> mealByCaution) {
        Map<String, Dimension> dimensions = new LinkedHashMap<>();
        dimensions.put(FAMILY_MEAL_FIT, mealDimension(candidate, family, mealByCaution));
        dimensions.put(TRIP_ROUTE_FIT, itineraryDimension(candidate, context));
        dimensions.put(MOBILITY_FIT, mobilityDimension(candidate, family));
        dimensions.put(AREA_DEMAND_SIGNAL, Dimension.notEvaluated());
        dimensions.put(REVIEW_SIGNAL, Dimension.notEvaluated());
        dimensions.put(LOCAL_MENU_FIT, localFoodDimension(candidate, context));
        dimensions.replaceAll((code, value) -> value.weighted(scoring.weight(code), switch (code) {
            case FAMILY_MEAL_FIT -> "가족 식사 조건과 공개 메뉴 근거를 비교했어요.";
            case TRIP_ROUTE_FIT -> "오늘의 중심 장소와 식당 사이 이동 맥락을 확인했어요.";
            case MOBILITY_FIT -> "도보·환승·주차 선호 근거를 확인했어요.";
            case AREA_DEMAND_SIGNAL -> "연결된 공식 지역 방문 수요 데이터가 없어 평가하지 않았어요.";
            case REVIEW_SIGNAL -> "연결된 공식 후기 데이터가 없어 평가하지 않았어요.";
            default -> "공개 메뉴와 Nutrition 참고 근거를 확인했어요.";
        }));
        var positives = new ArrayList<String>();
        var cautions = new ArrayList<String>();
        var checks = new ArrayList<String>();
        if (dimensions.get(MEAL).state() != EvidenceState.NOT_EVALUATED) {
            positives.add("표준 음식 참고값이 비교 가능한 메뉴 근거가 있어요.");
        } else if (family.hasScoredMealCaution()) {
            cautions.add("표준 음식 참고값이 비교 가능한 메뉴 근거가 충분하지 않아요.");
        }
        if ("REQUIRED".equals(family.parkingPreference()) && hasParkingInformation(candidate.parking())) {
            positives.add("주차 선호에 대해 공개된 정보를 함께 확인했어요.");
        }
        if (candidate.transit() != null) {
            positives.add("대중교통 경로의 예상시간·환승·명시 도보구간을 함께 살폈어요.");
            if (candidate.transit().unaccountedDistanceMeters() > 0) checks.add("도보 구간 일부는 카카오맵에서 자세히 확인해 주세요.");
        }
        if (family.stairsAvoidance()) checks.add("계단 여부는 공개 정보만으로 확정하기 어려워 방문 전에 확인해 주세요.");
        if (family.cautions().contains("INGREDIENT_CHECK")) {
            cautions.add("원재료 정보는 공개 데이터만으로 확인하기 어려워요.");
            checks.add("원재료와 조리 방식은 방문 전에 직접 확인해 주세요.");
        }
        if (!hasParkingInformation(candidate.parking()) && "REQUIRED".equals(family.parkingPreference())) {
            checks.add("주차 가능 여부는 방문 전에 확인해 주세요.");
        }
        if (candidate.menus().isEmpty()) checks.add("대표 메뉴 정보는 방문 전에 다시 확인해 주세요.");
        if (!family.allergens().isEmpty() || !family.avoidedFoods().isEmpty()) {
            checks.add("알레르기 재료·교차조리·실제 조리법은 방문 전 전화로 확인해 주세요.");
        }
        return new ScoredCandidate(candidate, Map.copyOf(dimensions), evidence(candidate, family), positives, cautions, checks);
    }

    private Dimension mobilityDimension(Candidate candidate, FamilyContext family) {
        var movement = movementDimension(candidate, family);
        var walking = walkingDimension(candidate, family);
        if (movement.state() == EvidenceState.NOT_EVALUATED) return walking;
        if (walking.state() == EvidenceState.NOT_EVALUATED) return movement;
        return new Dimension(Math.min(movement.score(), walking.score()),
                movement.state() == EvidenceState.PARTIAL || walking.state() == EvidenceState.PARTIAL
                        ? EvidenceState.PARTIAL : EvidenceState.EVALUATED);
    }

    private Dimension mealDimension(Candidate candidate, FamilyContext family, Map<String, Integer> values) {
        if (!family.hasScoredMealCaution() || values.isEmpty()) return Dimension.notEvaluated();
        return new Dimension(values.values().stream().min(Integer::compareTo).orElse(0), EvidenceState.EVALUATED);
    }

    private Dimension movementDimension(Candidate candidate, FamilyContext family) {
        if ("CAR".equals(family.transportMode())) {
            if (!hasParkingInformation(candidate.parking()) || "NO_PREFERENCE".equals(family.parkingPreference())) {
                return Dimension.notEvaluated();
            }
            return new Dimension(explicitlyNoParking(candidate.parking()) ? 0 : 100, EvidenceState.EVALUATED);
        }
        if (candidate.transit() == null) return Dimension.notEvaluated();
        double minutes = candidate.transit().totalTimeMinutes().doubleValue();
        int score = clamp((int) Math.round(100 - Math.min(70, minutes * 1.15 + candidate.transit().transferCount() * ("AVOID".equals(family.transferPreference()) ? 20 : 10))));
        return new Dimension(score, candidate.transit().unaccountedDistanceMeters() > 0
                ? EvidenceState.PARTIAL : EvidenceState.EVALUATED);
    }

    private Dimension walkingDimension(Candidate candidate, FamilyContext family) {
        if (candidate.transit() == null || family.shortestWalkingMinutes() <= 0) return Dimension.notEvaluated();
        long comfortableMeters = family.shortestWalkingMinutes() * 70L;
        long explicit = candidate.transit().explicitWalkingDistanceMeters();
        int score = explicit <= comfortableMeters ? 100
                : clamp((int) Math.round(100 - ((explicit - comfortableMeters) * 100.0 / Math.max(comfortableMeters, 1))));
        return new Dimension(score, candidate.transit().unaccountedDistanceMeters() > 0
                ? EvidenceState.PARTIAL : EvidenceState.EVALUATED);
    }

    private Dimension localFoodDimension(Candidate candidate, RequestContext context) {
        if (context.desiredLocalFood() == null || context.desiredLocalFood().isBlank()) {
            return candidate.menus().stream().anyMatch(MenuEvidence::usableForScoring)
                    ? new Dimension(70, EvidenceState.PARTIAL) : Dimension.notEvaluated();
        }
        String all = normalize(candidate.title() + " " + candidate.menus().stream().map(MenuEvidence::rawName)
                .collect(Collectors.joining(" ")));
        return new Dimension(all.contains(normalize(context.desiredLocalFood())) ? 100 : 40, EvidenceState.EVALUATED);
    }

    private Dimension itineraryDimension(Candidate candidate, RequestContext context) {
        if (!context.hasAnchor()) return Dimension.notEvaluated();
        if (candidate.transit() == null && candidate.distanceFromAnchorKm() != null) {
            int score = clamp((int) Math.round(100 - Math.min(70, candidate.distanceFromAnchorKm().doubleValue() * 5)));
            return new Dimension(score, EvidenceState.PARTIAL);
        }
        if (candidate.transit() == null) return Dimension.notEvaluated();
        return movementDimension(candidate, new FamilyContext("PUBLIC_TRANSIT", "NO_PREFERENCE", 0, false, "NO_PREFERENCE", List.of()));
    }

    private Map<String, Map<String, Integer>> nutrientScores(List<Candidate> candidates, List<String> cautions) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Candidate candidate : candidates) result.put(candidate.contentId(), new LinkedHashMap<>());
        for (String caution : cautions) {
            if (NUTRIENT_CAUTIONS.contains(caution)) scoreNutrient(candidates, caution, result);
            if ("SPICY".equals(caution)) scoreSpicy(candidates, result);
        }
        return result;
    }

    private void scoreNutrient(List<Candidate> candidates, String caution, Map<String, Map<String, Integer>> output) {
        var values = candidates.stream().map(candidate -> new Metric(candidate, nutrientValue(candidate, caution)))
                .filter(metric -> metric.value() != null).toList();
        if (values.isEmpty()) return;
        BigDecimal minimum = values.stream().map(Metric::value).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal maximum = values.stream().map(Metric::value).max(BigDecimal::compareTo).orElseThrow();
        for (Metric metric : values) {
            int score = maximum.compareTo(minimum) == 0 ? 100 : BigDecimal.valueOf(100)
                    .subtract(metric.value().subtract(minimum).multiply(BigDecimal.valueOf(60))
                            .divide(maximum.subtract(minimum), 4, RoundingMode.HALF_UP))
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            output.get(metric.candidate().contentId()).put(caution, clamp(score));
        }
    }

    private void scoreSpicy(List<Candidate> candidates, Map<String, Map<String, Integer>> output) {
        for (Candidate candidate : candidates) {
            if (candidate.menus().isEmpty()) continue;
            String menuText = normalize(candidate.menus().stream().map(MenuEvidence::rawName).collect(Collectors.joining(" ")));
            boolean spicy = SPICY_CUES.stream().anyMatch(cue -> menuText.contains(normalize(cue)));
            output.get(candidate.contentId()).put("SPICY", spicy ? 35 : 100);
        }
    }

    private BigDecimal nutrientValue(Candidate candidate, String caution) {
        return candidate.menus().stream().filter(MenuEvidence::usableForScoring).map(menu -> switch (caution) {
            case "SODIUM" -> menu.sodiumMg();
            case "SUGAR" -> menu.sugarG();
            case "CARBOHYDRATE" -> menu.carbohydrateG();
            default -> null;
        }).filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);
    }

    private InformationEvidence evidence(Candidate candidate, FamilyContext family) {
        int signals = 1; // bounded detail response exists for every candidate
        if (!candidate.menus().isEmpty()) signals++;
        if (candidate.menus().stream().anyMatch(MenuEvidence::usableForScoring)) signals++;
        if (hasParkingInformation(candidate.parking())) signals++;
        if (candidate.transit() != null) signals++;
        if (signals >= 5) return InformationEvidence.SUFFICIENT;
        if (signals >= 3) return InformationEvidence.REFERENCE;
        if (signals == 2) return InformationEvidence.LIMITED;
        return InformationEvidence.CHECK_REQUIRED;
    }

    private static boolean hasParkingInformation(String parking) { return parking != null && !parking.isBlank(); }
    private static boolean explicitlyNoParking(String parking) {
        if (!hasParkingInformation(parking)) return false;
        String value = normalize(parking);
        return List.of("주차불가", "주차 불가", "주차없음", "주차 없음", "주차 미제공", "주차불가능")
                .stream().anyMatch(value::contains);
    }
    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    private static int stableContentIdCompare(String left, String right) {
        try { return Long.compare(Long.parseLong(left), Long.parseLong(right)); }
        catch (NumberFormatException ignored) { return left.compareTo(right); }
    }

    public enum EvidenceState { EVALUATED, PARTIAL, NOT_EVALUATED }
    public enum InformationEvidence {
        CHECK_REQUIRED(0), LIMITED(1), REFERENCE(2), SUFFICIENT(3);
        private final int rank;
        InformationEvidence(int rank) { this.rank = rank; }
        public int rank() { return rank; }
    }
    public enum Perspective {
        BALANCED(Map.of()), MEAL_PRIORITY(Map.of()), MOBILITY_PRIORITY(Map.of());
        private final Map<String, Integer> weights;
        Perspective(Map<String, Integer> weights) { this.weights = Map.copyOf(weights); }
        public Map<String, Integer> weights() { return weights; }
    }
    public record FamilyContext(String transportMode, String parkingPreference, int shortestWalkingMinutes,
            boolean stairsAvoidance, String transferPreference, List<String> cautions,
            List<String> allergens, List<String> avoidedFoods) {
        public FamilyContext { cautions = List.copyOf(cautions); allergens = List.copyOf(allergens); avoidedFoods = List.copyOf(avoidedFoods); }
        public FamilyContext(String transportMode, String parkingPreference, int shortestWalkingMinutes,
                boolean stairsAvoidance, String transferPreference, List<String> cautions) {
            this(transportMode, parkingPreference, shortestWalkingMinutes, stairsAvoidance, transferPreference,
                    cautions, List.of(), List.of());
        }
        boolean hasScoredMealCaution() { return cautions.stream().anyMatch(caution -> NUTRIENT_CAUTIONS.contains(caution) || "SPICY".equals(caution)); }
    }
    public record RequestContext(String desiredLocalFood, List<String> strictExclusions, boolean hasAnchor,
            BigDecimal maximumTransitMinutes) {
        public RequestContext { strictExclusions = strictExclusions == null ? List.of() : List.copyOf(strictExclusions); }
    }
    public record Candidate(String contentId, String title, String areaLabel, String address, String imageUrl, String parking,
            List<MenuEvidence> menus, TransitEvidence transit, kr.hankkitravel.shared.geo.Coordinates coordinates,
            BigDecimal distanceFromAnchorKm,
            String phone, String placeUrl, String contactEvidence) {
        public Candidate { menus = List.copyOf(menus); }
        public Candidate(String contentId, String title, String address, String imageUrl, String parking,
                List<MenuEvidence> menus, TransitEvidence transit) {
            this(contentId,title,null,address,imageUrl,parking,menus,transit,null,null,null,null,"NONE");
        }
        public Candidate withTransit(TransitEvidence transit) { return new Candidate(contentId,title,areaLabel,address,imageUrl,parking,menus,transit,coordinates,distanceFromAnchorKm,phone,placeUrl,contactEvidence); }
        public Candidate withDistance(BigDecimal distance) { return new Candidate(contentId,title,areaLabel,address,imageUrl,parking,menus,transit,coordinates,distance,phone,placeUrl,contactEvidence); }
    }
    public record MenuEvidence(String rawName, String matchLevel, BigDecimal sodiumMg, BigDecimal sugarG,
            BigDecimal carbohydrateG, String standardFood) {
        boolean usableForScoring() { return ("HIGH".equals(matchLevel) || "MEDIUM".equals(matchLevel)) && standardFood != null; }
    }
    public record TransitEvidence(BigDecimal totalTimeMinutes, int transferCount, long explicitWalkingDistanceMeters,
            long unaccountedDistanceMeters, String landingUrl) { }
    public record Dimension(int score, EvidenceState state, int weight, List<String> reasons) {
        public Dimension(int score, EvidenceState state) { this(score, state, 0, List.of()); }
        public Dimension { reasons = List.copyOf(reasons); }
        public boolean evaluated() { return state != EvidenceState.NOT_EVALUATED; }
        public int awardedPoints() { return evaluated() ? (int)Math.round(score * weight / 100.0) : 0; }
        public int maxPoints() { return evaluated() ? weight : 0; }
        Dimension weighted(int value, String reason) { return new Dimension(score, state, value, List.of(reason)); }
        static Dimension notEvaluated() { return new Dimension(0, EvidenceState.NOT_EVALUATED); }
    }
    public record ScoredCandidate(Candidate candidate, Map<String, Dimension> dimensions,
            InformationEvidence informationEvidence, List<String> positives, List<String> cautions, List<String> checks) {
        public ScoredCandidate { positives = List.copyOf(positives); cautions = List.copyOf(cautions); checks = List.copyOf(checks); }
    }
    public record RankedCandidate(ScoredCandidate candidate, Perspective perspective, int compatibilityScore,
            int evaluatedWeight, int evidenceCoverage) {
        int dimensionScore(String name) { return candidate.dimensions().getOrDefault(name, Dimension.notEvaluated()).score(); }
    }
    public record HardDecision(boolean included, List<String> reasons) { public HardDecision { reasons = List.copyOf(reasons); } }
    private record Metric(Candidate candidate, BigDecimal value) { }
}
