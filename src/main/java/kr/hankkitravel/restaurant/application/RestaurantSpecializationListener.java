package kr.hankkitravel.restaurant.application;

import kr.hankkitravel.restaurant.persistence.RestaurantSpecializationMapper;
import kr.hankkitravel.tourism.model.TourismPlaceCached;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class RestaurantSpecializationListener {
    private final RestaurantSpecializationMapper restaurants;

    public RestaurantSpecializationListener(RestaurantSpecializationMapper restaurants) {
        this.restaurants = restaurants;
    }

    @EventListener
    public void on(TourismPlaceCached event) {
        if (event.isRestaurant()) {
            restaurants.ensure(event.tourismPlaceId());
        }
    }
}
