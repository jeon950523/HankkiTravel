package kr.hankkitravel.tourism.model;

import java.util.List;
import java.util.Objects;

/** Parsed list envelope metadata is retained so pagination can be proven complete. */
public record TourApiPage(List<TourismPlace> items, int pageNo, int numOfRows, int totalCount) {
    public TourApiPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (pageNo < 1 || numOfRows < 1 || totalCount < 0) {
            throw new IllegalArgumentException("Invalid TourAPI page metadata");
        }
    }
}
