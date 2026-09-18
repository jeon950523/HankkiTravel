package kr.hankkitravel.trip.application;

import java.util.HashMap;
import java.util.Map;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.tourism.application.TourismRealtimeGateway;
import kr.hankkitravel.trip.model.TripPlannerRows;
import org.springframework.stereotype.Service;

/** Hydrates resolver output from TourAPI without persisting live place data. */
@Service
public final class DayRecommendationOriginService {
    private final TripPlaceScheduleService schedules;
    private final TourismRealtimeGateway tourism;
    private final DayRecommendationContextResolver resolver;

    public DayRecommendationOriginService(TripPlaceScheduleService schedules, TourismRealtimeGateway tourism,
            DayRecommendationContextResolver resolver) {
        this.schedules = schedules;
        this.tourism = tourism;
        this.resolver = resolver;
    }

    public Context resolve(String guest, String trip, int day, String targetSlot) {
        var current = schedules.references(guest, trip, day);
        var chronology = day > 1
                ? resolver.resolve(schedules.references(guest, trip, day - 1), current, targetSlot)
                : resolver.resolve(current, targetSlot);
        return hydrate(chronology);
    }

    public Context resolve(TripPlaceScheduleService.DayReferences refs, String targetSlot) {
        return hydrate(resolver.resolve(refs, targetSlot));
    }

    private Context hydrate(DayRecommendationContextResolver.Context chronology) {
        var cache = new HashMap<String, Hydrated>();
        TripPlannerRows.Reference selected = null;
        Coordinates origin = null;
        for (var candidate : chronology.originCandidates()) {
            var hydrated = hydrate(candidate, cache);
            if (hydrated.coordinates() != null) {
                selected = candidate;
                origin = hydrated.coordinates();
                break;
            }
        }
        Coordinates previousStay = hydrate(chronology.previousDayStay(), cache).coordinates();
        Coordinates currentFocus = hydrate(chronology.currentDayFocus(), cache).coordinates();
        int calls = cache.values().stream().mapToInt(Hydrated::calls).sum();
        return new Context(origin, selected == null ? "REGION" : selected.getSlotType(), previousStay, currentFocus, calls);
    }

    private Hydrated hydrate(TripPlannerRows.Reference reference, Map<String, Hydrated> cache) {
        if (reference == null || reference.getContentId() == null) return Hydrated.empty();
        return cache.computeIfAbsent(reference.getContentId(), ignored -> {
            try {
                return new Hydrated(tourism.place(reference.getContentId()).coordinates(), 1);
            } catch (RuntimeException exception) {
                return new Hydrated(null, 1);
            }
        });
    }

    public record Context(Coordinates origin, String originSlot, Coordinates previousDayStay,
            Coordinates currentDayFocus, int detailCalls) {
        public boolean hasOrigin() { return origin != null; }
    }

    private record Hydrated(Coordinates coordinates, int calls) {
        static Hydrated empty() { return new Hydrated(null, 0); }
    }
}
