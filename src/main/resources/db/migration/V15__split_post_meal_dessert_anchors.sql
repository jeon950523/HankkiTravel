ALTER TABLE trip_day_place_anchors DROP CONSTRAINT ck_trip_day_place_anchor_slot;
ALTER TABLE trip_day_place_anchors ADD CONSTRAINT ck_trip_day_place_anchor_slot
    CHECK (slot_type IN ('DAY_FOCUS','MORNING_ACTIVITY','AFTERNOON_ACTIVITY',
                         'POST_MEAL_DESSERT','POST_LUNCH_DESSERT','POST_DINNER_DESSERT','STAY'));
