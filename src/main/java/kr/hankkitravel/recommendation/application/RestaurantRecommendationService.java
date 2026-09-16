package kr.hankkitravel.recommendation.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import kr.hankkitravel.profile.application.FamilyProfileApplicationService;
import kr.hankkitravel.profile.application.FamilyProfileSnapshot;
import kr.hankkitravel.recommendation.application.RecommendationCore.Candidate;
import kr.hankkitravel.recommendation.application.RecommendationCore.AreaDemandEvidence;
import kr.hankkitravel.recommendation.application.RecommendationCore.FamilyContext;
import kr.hankkitravel.recommendation.application.RecommendationCore.InformationEvidence;
import kr.hankkitravel.recommendation.application.RecommendationCore.MenuEvidence;
import kr.hankkitravel.recommendation.application.RecommendationCore.Perspective;
import kr.hankkitravel.recommendation.application.RecommendationCore.RequestContext;
import kr.hankkitravel.recommendation.application.RecommendationCore.TransitEvidence;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.tourism.model.TourismNutritionEvidence;
import kr.hankkitravel.transit.application.TransitRouteFinder;
import kr.hankkitravel.transit.model.TransitRoute;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RestaurantRecommendationService {
    private static final String ATTRIBUTION = "출처: ⓒ한국관광공사";
    private static final String NUTRITION_NOTICE = "표준 음식 기준 또는 유사 음식 기준 참고정보이며, 특정 식당의 실제 제공량·레시피·섭취량이 아닙니다.";
    private final FamilyProfileApplicationService profiles;
    private final TourismRealtimeGateway tourism;
    private final TransitRouteFinder transit;
    private final AreaDemandSignalProvider areaDemand;
    private final ContactEnrichmentProvider contacts;
    private final RecommendationCore core;
    private final int listPageSize;
    private final int prefilterLimit;
    private final int detailLimit;
    private final int transitLimit;

    public RestaurantRecommendationService(FamilyProfileApplicationService profiles, TourismRealtimeGateway tourism,
            TransitRouteFinder transit, AreaDemandSignalProvider areaDemand, ContactEnrichmentProvider contacts,
            RecommendationScoringProperties scoring,
            @Value("${hankki.recommendation.list-page-size:16}") int listPageSize,
            @Value("${hankki.recommendation.prefilter-limit:10}") int prefilterLimit,
            @Value("${hankki.recommendation.detail-limit:6}") int detailLimit,
            @Value("${hankki.recommendation.transit-limit:3}") int transitLimit) {
        this.profiles = profiles;
        this.tourism = tourism;
        this.transit = transit;
        this.areaDemand = areaDemand;
        this.contacts = contacts;
        this.core = new RecommendationCore(scoring);
        if (listPageSize < 1 || prefilterLimit < 1 || detailLimit < 1 || transitLimit < 1
                || prefilterLimit > listPageSize || detailLimit > prefilterLimit || transitLimit > detailLimit) {
            throw new IllegalArgumentException("추천 후보 호출 설정을 확인하세요.");
        }
        this.listPageSize = listPageSize;
        this.prefilterLimit = prefilterLimit;
        this.detailLimit = detailLimit;
        this.transitLimit = transitLimit;
    }

    public RecommendationResult recommend(RecommendationCommand command) {
        validate(command);
        long started = System.nanoTime();
        FamilyProfileSnapshot profile = profiles.owned(command.guestPublicId(), command.profileId());
        String region = canonicalRegion(command.region());
        var page = tourism.restaurants(region, 0, listPageSize);
        var context = new RequestContext(command.desiredLocalFood(), command.strictExclusions(), command.anchor() != null,
                command.maximumTransitMinutes());
        var family = family(profile);
        var details = new ArrayList<CandidateWithCoordinates>();
        int detailCalls = 0;
        int kakaoLocalCalls = 0;
        for (var place : page.items().stream().filter(place -> place.contentId() != null && !place.contentId().isBlank())
                .sorted(Comparator.comparing(kr.hankkitravel.tourism.model.TourismPlace::contentId,
                        RestaurantRecommendationService::stableIdCompare))
                .limit(prefilterLimit).toList()) {
            if (details.size() >= detailLimit) break;
            var live = tourism.decisionData(place.contentId());
            detailCalls++;
            String classification = live.restaurant().classification();
            if (!"MEAL".equals(classification) && !"MIXED".equals(classification)) continue;
            String phone=blankToNull(live.detail().telephone());
            BigDecimal distance=command.anchor()==null||place.coordinates()==null?null:
                    BigDecimal.valueOf(distanceKm(command.anchor(),place.coordinates())).setScale(2,java.math.RoundingMode.HALF_UP);
            var candidate = new Candidate(place.contentId(), live.detail().title(), areaLabel(live.detail().address()),
                    live.detail().address(), live.detail().firstImage(), live.detail().parking(), menus(live.nutritionMatches()),
                    null,place.coordinates(),distance,phone,null,phone==null?"UNAVAILABLE":"KTO_DIRECT");
            var contact = contacts.enrich(candidate.title(), candidate.address(), place.coordinates());
            kakaoLocalCalls++;
            if (contact.status() == ContactEnrichmentProvider.Status.MATCHED) {
                String selectedPhone = phone == null ? contact.phone() : phone;
                String evidence = phone == null ? "KAKAO_STRICT_MATCH" : "KTO_DIRECT";
                candidate = candidate.withContact(selectedPhone, contact.placeUrl(), evidence);
            }
            if (core.hardDecision(candidate, family, context).included()) {
                AreaDemandSignalProvider.RegionKey regionKey = regionKey(live.detail());
                details.add(new CandidateWithCoordinates(candidate, place.coordinates(), regionKey));
            }
        }

        Map<String, AreaDemandSignalProvider.Signal> demandByRegion = new LinkedHashMap<>();
        int demandStrengthCalls = 0;
        int resourceDemandCalls = 0;
        var withDemand = new ArrayList<CandidateWithCoordinates>();
        for (var entry : details) {
            var key = entry.regionKey();
            if (key == null) { withDemand.add(entry); continue; }
            var signal = demandByRegion.get(key.cacheKey());
            if (signal == null) {
                signal = areaDemand.signal(key, command.tripDate());
                demandByRegion.put(key.cacheKey(), signal);
                demandStrengthCalls += signal.demandStrengthCalls();
                resourceDemandCalls += signal.resourceDemandCalls();
            }
            Candidate enriched = signal.evaluated() ? entry.candidate().withAreaDemand(new AreaDemandEvidence(true,
                    signal.score(), signal.referencePeriod(), signal.reason(), signal.sourceAttributions())) : entry.candidate();
            withDemand.add(new CandidateWithCoordinates(enriched, entry.coordinates(), key));
        }
        details = withDemand;

        int transitCalls = 0;
        boolean transitUnavailable = false;
        if ("PUBLIC_TRANSIT".equals(family.transportMode()) && command.anchor() != null && !details.isEmpty()) {
            var preliminary = core.score(details.stream().map(CandidateWithCoordinates::candidate).toList(), family, context);
            var finalIds = core.rank(preliminary, Perspective.BALANCED).stream()
                    .map(item -> item.candidate().candidate().contentId()).toList();
            var withTransit = new ArrayList<CandidateWithCoordinates>();
            for (var entry : details) {
                Candidate candidate = entry.candidate();
                if (finalIds.contains(candidate.contentId()) && entry.coordinates() != null && transitCalls < transitLimit) {
                    transitCalls++;
                    try {
                        var selected = selectRoute(transit.findRoutes(command.anchor(), entry.coordinates()));
                        if (selected != null) candidate = candidate.withTransit(toEvidence(selected));
                    } catch (IntegrationException exception) {
                        transitUnavailable = true;
                    }
                }
                if (core.hardDecision(candidate, family, context).included()) {
                    withTransit.add(new CandidateWithCoordinates(candidate, entry.coordinates(), entry.regionKey()));
                }
            }
            details = withTransit;
        }

        var scored = core.score(details.stream().map(CandidateWithCoordinates::candidate).toList(), family, context);
        boolean mobilityEvaluated = scored.stream().anyMatch(candidate -> candidate.dimensions()
                .get(RecommendationCore.MOVEMENT).state() != RecommendationCore.EvidenceState.NOT_EVALUATED);
        var perspectives = new ArrayList<PerspectiveResult>();
        for (Perspective perspective : Perspective.values()) {
            boolean requiresAnchor = perspective == Perspective.MOBILITY_PRIORITY && "PUBLIC_TRANSIT".equals(family.transportMode())
                    && !mobilityEvaluated;
            var candidates = requiresAnchor ? List.<RecommendationCore.RankedCandidate>of() : core.rank(scored, perspective);
            perspectives.add(new PerspectiveResult(perspective.name(), requiresAnchor ? "MOVEMENT_CONTEXT_REQUIRED" : "READY",
                    requiresAnchor ? "이동편의 비교를 위해 기준 장소를 추가해 주세요." : null, candidates));
        }
        var topCandidates=core.rank(scored,Perspective.BALANCED);
        return new RecommendationResult(new ResultContext(region, command.tripDate(), command.mealType(), command.startMode()),
                perspectives, topCandidates, scored.size(), new CallSummary(1, detailCalls, transitCalls,
                        demandStrengthCalls, resourceDemandCalls, kakaoLocalCalls,
                        Duration.ofNanos(System.nanoTime() - started).toMillis()),
                transitUnavailable ? "TRANSIT_PARTIALLY_UNAVAILABLE" : "CURRENT_DATA", ATTRIBUTION, NUTRITION_NOTICE);
    }

    private FamilyContext family(FamilyProfileSnapshot profile) {
        int shortestWalking = profile.members().stream().mapToInt(FamilyProfileSnapshot.Member::continuousWalkingMinutes)
                .min().orElse(0);
        List<String> cautions = profile.members().stream().flatMap(member -> member.mealCautions().stream())
                .filter(value -> !"NONE".equals(value)).distinct().toList();
        List<String> allergens=profile.members().stream().flatMap(member->member.allergenRestrictions().stream()).distinct().toList();
        List<String> avoided=profile.members().stream().flatMap(member->member.avoidedFoods().stream()).distinct().toList();
        return new FamilyContext(profile.transportMode(), profile.parkingPreference(), shortestWalking,
                profile.stairsAvoidance(), profile.transferPreference(), cautions,allergens,avoided);
    }

    private List<MenuEvidence> menus(List<TourismNutritionEvidence> source) {
        return source.stream().map(evidence -> {
            var reference = evidence.referenceNutrition();
            return new MenuEvidence(evidence.rawMenuName(), evidence.matchLevel(), reference == null ? null : reference.sodiumMg(),
                    reference == null ? null : reference.sugarG(), reference == null ? null : reference.carbohydrateG(),
                    evidence.matchedStandardFood(), evidence.reviewState());
        }).toList();
    }

    private TransitRoute selectRoute(kr.hankkitravel.transit.model.TransitResult result) {
        if (result == null || result.routes().isEmpty()) return null;
        return result.routes().stream().min(Comparator.comparing(this::routeBurden)
                .thenComparing(TransitRoute::totalTimeMinutes).thenComparingInt(TransitRoute::transferCount)
                .thenComparingLong(TransitRoute::explicitWalkingDistanceMeters)).orElse(null);
    }

    private BigDecimal routeBurden(TransitRoute route) {
        return route.totalTimeMinutes().add(BigDecimal.valueOf(route.transferCount() * 8L))
                .add(BigDecimal.valueOf(route.explicitWalkingDistanceMeters()).divide(BigDecimal.valueOf(70), 2,
                        java.math.RoundingMode.HALF_UP));
    }

    private TransitEvidence toEvidence(TransitRoute route) {
        return new TransitEvidence(route.totalTimeMinutes(), route.transferCount(), route.explicitWalkingDistanceMeters(),
                route.unaccountedDistanceMeters(), route.kakaoMapLandingUrl());
    }

    private void validate(RecommendationCommand command) {
        if (command == null || command.guestPublicId() == null || command.profileId() <= 0 || command.tripDate() == null) {
            throw new IllegalArgumentException("추천 요청의 프로필과 식사 맥락을 확인해 주세요.");
        }
        canonicalRegion(command.region());
        if (!List.of("BREAKFAST", "LUNCH", "DINNER").contains(command.mealType())
                || !List.of("MEAL_FIRST", "PLACE_FIRST").contains(command.startMode())) {
            throw new IllegalArgumentException("식사 유형과 시작 방식을 확인해 주세요.");
        }
        if ("PLACE_FIRST".equals(command.startMode()) && command.anchor() == null) {
            throw new IllegalArgumentException("장소부터 시작하려면 기준 장소 좌표가 필요합니다.");
        }
        if (command.maximumTransitMinutes() != null && command.maximumTransitMinutes().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("최대 이동시간을 확인해 주세요.");
        }
    }

    private String canonicalRegion(String value) {
        if (value == null) throw new IllegalArgumentException("지역을 선택해 주세요.");
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "JEJU", "JEJU_CITY", "JEJU-CITY" -> "jeju-city";
            case "GYEONGJU" -> "gyeongju";
            default -> throw new IllegalArgumentException("제주 또는 경주를 선택해 주세요.");
        };
    }

    private static int stableIdCompare(String left, String right) {
        try { return Long.compare(Long.parseLong(left), Long.parseLong(right)); }
        catch (NumberFormatException ignored) { return left.compareTo(right); }
    }
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String areaLabel(String address){if(address==null||address.isBlank())return null;var parts=address.trim().split("\\s+");return String.join(" ",java.util.Arrays.copyOf(parts,Math.min(3,parts.length)));}
    private static AreaDemandSignalProvider.RegionKey regionKey(kr.hankkitravel.tourism.model.TourismRestaurantDetail detail) {
        if (detail.regionCode() == null || detail.regionCode().isBlank()
                || detail.districtCode() == null || detail.districtCode().isBlank()) return null;
        String district = detail.districtCode().length() == 3 ? detail.regionCode() + detail.districtCode() : detail.districtCode();
        return new AreaDemandSignalProvider.RegionKey(detail.regionCode(), district, areaLabel(detail.address()));
    }
    private static double distanceKm(Coordinates a,Coordinates b){double lat1=Math.toRadians(a.latitude().doubleValue()),lat2=Math.toRadians(b.latitude().doubleValue());double dlat=lat2-lat1,dlon=Math.toRadians(b.longitude().doubleValue()-a.longitude().doubleValue());double h=Math.sin(dlat/2)*Math.sin(dlat/2)+Math.cos(lat1)*Math.cos(lat2)*Math.sin(dlon/2)*Math.sin(dlon/2);return 6371*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}

    public record RecommendationCommand(String guestPublicId, long profileId, String region, LocalDate tripDate,
            String mealType, String startMode, Coordinates anchor, String desiredLocalFood, List<String> strictExclusions,
            BigDecimal maximumTransitMinutes) {
        public RecommendationCommand { strictExclusions = strictExclusions == null ? List.of() : List.copyOf(strictExclusions); }
    }
    public record ResultContext(String region, LocalDate tripDate, String mealType, String startMode) { }
    public record CallSummary(int tourListCalls, int tourDetailCalls, int kakaoTransitCalls,
            int demandStrengthCalls, int resourceDemandCalls, int kakaoLocalCalls, long elapsedMillis) { }
    public record PerspectiveResult(String perspective, String status, String message,
            List<RecommendationCore.RankedCandidate> candidates) { public PerspectiveResult { candidates = List.copyOf(candidates); } }
    public record RecommendationResult(ResultContext context, List<PerspectiveResult> perspectives,
            List<RecommendationCore.RankedCandidate> topCandidates, int candidateCount,
            CallSummary callSummary, String dataAvailability, String sourceAttribution, String nutritionNotice) {
        public RecommendationResult { perspectives = List.copyOf(perspectives); topCandidates=List.copyOf(topCandidates); }
    }
    private record CandidateWithCoordinates(Candidate candidate, Coordinates coordinates,
            AreaDemandSignalProvider.RegionKey regionKey) { }
}
