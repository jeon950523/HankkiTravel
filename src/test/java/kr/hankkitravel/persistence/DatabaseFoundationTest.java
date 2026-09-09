package kr.hankkitravel.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import kr.hankkitravel.identity.model.*;
import kr.hankkitravel.identity.persistence.*;
import kr.hankkitravel.profile.model.*;
import kr.hankkitravel.profile.persistence.*;
import kr.hankkitravel.restaurant.model.Restaurant;
import kr.hankkitravel.restaurant.persistence.RestaurantMapper;
import kr.hankkitravel.shared.geo.Coordinates;
import kr.hankkitravel.tourism.model.*;
import kr.hankkitravel.tourism.persistence.StoredTourismPlaceMapper;
import kr.hankkitravel.trip.model.Trip;
import kr.hankkitravel.trip.persistence.TripMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DatabaseFoundationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(
            "mysql@sha256:c831a0f11348d402b43d77453e17d770be2eef356615a2823fe0f5a0d6c8b9af")
            .withDatabaseName("hankki_p02_test").withUsername("foundation_test")
            .withPassword(UUID.randomUUID().toString())
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4");

    private static int initialTableCount = -1;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        String url = MYSQL.getJdbcUrl() + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        try (var connection = DriverManager.getConnection(url, MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?")) {
            statement.setString(1, MYSQL.getDatabaseName());
            try (var result = statement.executeQuery()) { result.next(); initialTableCount = result.getInt(1); }
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired UserMapper users;
    @Autowired GuestMapper guests;
    @Autowired FamilyProfileMapper profiles;
    @Autowired FamilyMemberMapper members;
    @Autowired StoredTourismPlaceMapper places;
    @Autowired RestaurantMapper restaurants;
    @Autowired TripMapper trips;

    @Test void emptyDatabaseMigratesV1ThroughV8AndRestartHasNothingPending() {
        assertThat(initialTableCount).isZero();
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().toString())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(flyway.info().pending()).isEmpty();
        var restarted = Flyway.configure().dataSource(flyway.getConfiguration().getDataSource())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertThat(restarted.info().pending()).isEmpty();
        assertThat(restarted.validateWithResult().validationSuccessful).isTrue();
    }

    @Test void v1BytesAreUnchanged() throws Exception {
        try (var in = getClass().getResourceAsStream("/db/migration/V1__foundation.sql")) {
            assertThat(in).isNotNull();
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
            assertThat(checksum).isEqualTo("a2b617623a812790ebbc62d8995dcf4dc434e72ef40a3f095a20cb7522db2ffc");
        }
    }

    @Test void userInsertReadAndUtcMicrosecondTimestamps() {
        var before = Instant.now().minusSeconds(2);
        var user = user();
        var saved = users.findById(user.getId());
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedAt()).isBetween(before, Instant.now().plusSeconds(2));
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");
    }

    @Test void guestInsertReadAndOpaqueIdentifierUnique() {
        var publicId = UUID.randomUUID();
        var guest = new Guest(publicId);
        guests.insert(guest);
        assertThat(guests.findById(guest.getId()).getPublicId()).isEqualTo(publicId.toString());
        assertThatThrownBy(() -> guests.insert(new Guest(publicId))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void userAndGuestProfileOwnershipRoundTrip() {
        var user = user();
        var guest = guest();
        var userProfile = new FamilyProfile(new ProfileOwnership(user.getId(), null), "사용자 프로필");
        var guestProfile = new FamilyProfile(new ProfileOwnership(null, guest.getId()), "게스트 프로필");
        profiles.insert(userProfile); profiles.insert(guestProfile);
        assertThat(profiles.findById(userProfile.getId()).ownership()).isEqualTo(new ProfileOwnership(user.getId(), null));
        assertThat(profiles.findById(guestProfile.getId()).ownership()).isEqualTo(new ProfileOwnership(null, guest.getId()));
    }

    @Test void invalidDualAndMissingOwnershipRejectedByDatabaseAndApplication() {
        var user = user(); var guest = guest();
        assertThatThrownBy(() -> new ProfileOwnership(user.getId(), guest.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfileOwnership(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertCheckViolation(() -> jdbc.update("INSERT INTO family_profiles (owner_user_id, owner_guest_id, name) VALUES (?, ?, ?)",
                user.getId(), guest.getId(), "invalid"), "ck_profiles_one_owner");
        assertCheckViolation(() -> jdbc.update("INSERT INTO family_profiles (name) VALUES (?)", "invalid"),
                "ck_profiles_one_owner");
    }

    @Test void profileOwnershipForeignKeysAndParentDeletionAreRestricted() {
        assertThatThrownBy(() -> profiles.insert(new FamilyProfile(new ProfileOwnership(Long.MAX_VALUE, null), "invalid")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> profiles.insert(new FamilyProfile(new ProfileOwnership(null, Long.MAX_VALUE), "invalid")))
                .isInstanceOf(DataIntegrityViolationException.class);
        var user = user();
        profiles.insert(new FamilyProfile(new ProfileOwnership(user.getId(), null), "retained"));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", user.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void ownershipTransferCanBeAtomicWithoutIntermediateInvalidOwner() {
        var guest = guest(); var user = user();
        var profile = new FamilyProfile(new ProfileOwnership(null, guest.getId()), "transfer");
        profiles.insert(profile);
        assertThat(jdbc.update("UPDATE family_profiles SET owner_user_id = ?, owner_guest_id = NULL WHERE id = ?",
                user.getId(), profile.getId())).isEqualTo(1);
        assertThat(profiles.findById(profile.getId()).ownership()).isEqualTo(new ProfileOwnership(user.getId(), null));
    }

    @Test void memberMappingAndProfileForeignKey() {
        var profile = profile();
        var member = new FamilyMember(new FamilyProfileId(profile.getId()), "동행자", 0);
        members.insert(member);
        var saved = members.findById(member.getId());
        assertThat(saved.getNickname()).isEqualTo("동행자");
        assertThat(saved.getProfileId()).isEqualTo(profile.getId());
        assertThat(saved.getSortOrder()).isZero();
        assertThatThrownBy(() -> members.insert(new FamilyMember(new FamilyProfileId(Long.MAX_VALUE), "invalid", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void tourismIdentifierUniqueAndCoordinatesPreserveEvidencePrecision() {
        var place = place();
        var saved = places.findById(place.getId());
        assertThat(saved.getContentId()).isEqualTo("2847726");
        assertThat(saved.getId().toString()).isNotEqualTo(saved.getContentId());
        assertThat(saved.getLongitude()).isEqualByComparingTo("126.3584772018");
        assertThat(saved.getLatitude()).isEqualByComparingTo("33.4394340828");
        assertThat(saved.getLDongRegnCd()).isEqualTo("50");
        assertThat(saved.getLDongSignguCd()).isEqualTo("110");
        assertThat(saved.getSourceModifiedAt()).isEqualTo(Instant.parse("2026-09-08T03:04:05.123456Z"));
        assertThat(saved.isActive()).isTrue();
        assertThatThrownBy(this::place).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void unavailableCoordinatesRemainNullAndInvalidPairsAreRejected() {
        var place = new StoredTourismPlace("missing-coordinate", "12", "fixture", null, null, null, null);
        places.insert(place);
        assertThat(places.findById(place.getId()).getLongitude()).isNull();
        assertCheckViolation(() -> jdbc.update("UPDATE tourism_places SET longitude = 126 WHERE id = ?", place.getId()),
                "ck_tourism_coordinates");
        assertCheckViolation(() -> jdbc.update("UPDATE tourism_places SET longitude = 181, latitude = 35 WHERE id = ?", place.getId()),
                "ck_tourism_coordinates");
        assertCheckViolation(() -> jdbc.update("UPDATE tourism_places SET active = 2 WHERE id = ?", place.getId()),
                "ck_tourism_active");
    }

    @Test void restaurantSpecializationOneToOneAndForeignKey() {
        var place = new StoredTourismPlace("restaurant-fixture", "39", "음식점 회귀 fixture", "50", "110", null, null);
        places.insert(place);
        var restaurant = new Restaurant(new TourismPlaceId(place.getId()));
        restaurants.insert(restaurant);
        assertThat(restaurants.findById(restaurant.getId()).getTourismPlaceId()).isEqualTo(place.getId());
        assertThatThrownBy(() -> restaurants.insert(new Restaurant(new TourismPlaceId(place.getId()))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> restaurants.insert(new Restaurant(new TourismPlaceId(Long.MAX_VALUE))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void tripMappingAndProfileForeignKey() {
        var profile = profile();
        var trip = new Trip(new FamilyProfileId(profile.getId()), "DRAFT");
        trips.insert(trip);
        assertThat(trips.findById(trip.getId()).getProfileId()).isEqualTo(profile.getId());
        assertThat(trips.findById(trip.getId()).getStatus()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> trips.insert(new Trip(new FamilyProfileId(Long.MAX_VALUE), "DRAFT")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void schemaEnginePrecisionIndexesAndNoProductionSeed() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN
                ('users','guests','family_profiles','family_members','tourism_places','restaurants','trips','tourism_sync_runs','tourism_sync_scope_states')
                AND engine = 'InnoDB' AND table_collation LIKE 'utf8mb4%'
                """, Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name <> 'foundation_metadata'
                AND column_name IN ('created_at','updated_at','source_modified_at')
                AND data_type = 'datetime' AND datetime_precision = 6
                """, Integer.class)).isEqualTo(16);
        assertThat(jdbc.queryForList("""
                SELECT DISTINCT index_name FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                """, String.class)).contains("ix_profiles_user", "ix_profiles_guest",
                "ix_tourism_region_type", "ix_tourism_source_modified", "ix_trips_profile_status");
        for (String table : new String[]{"users","guests","family_profiles","family_members","tourism_places","restaurants","trips","tourism_sync_runs","tourism_sync_scope_states"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'nutrition%'", Integer.class))
                .isZero();
    }


    @Test void allSuppliedTourApiCoordinatesRoundTripWithoutRounding() throws Exception {
        try (var stream = getClass().getResourceAsStream("/tourism/evidence-coordinates.csv")) {
            assertThat(stream).isNotNull();
            var rows = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().skip(1).toList();
            assertThat(rows).hasSize(400);
            int maximumScale = 0;
            for (int i = 0; i < rows.size(); i++) {
                var values = rows.get(i).split(",");
                var longitude = new BigDecimal(values[0]);
                var latitude = new BigDecimal(values[1]);
                maximumScale = Math.max(maximumScale, Math.max(longitude.scale(), latitude.scale()));
                var place = new StoredTourismPlace("precision-" + i, "12", "좌표 정밀도 fixture",
                        null, null, new Coordinates(longitude, latitude), null);
                places.insert(place);
                var saved = places.findById(place.getId());
                assertThat(saved.getLongitude()).as("longitude row %s", i).isEqualByComparingTo(longitude);
                assertThat(saved.getLatitude()).as("latitude row %s", i).isEqualByComparingTo(latitude);
            }
            assertThat(maximumScale).isEqualTo(15);
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'tourism_places'
                AND column_name IN ('longitude','latitude')
                AND numeric_precision = 18 AND numeric_scale = 15
                """, Integer.class)).isEqualTo(2);
    }

    private void assertCheckViolation(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String constraint) {
        assertThatThrownBy(action).isInstanceOfSatisfying(org.springframework.jdbc.UncategorizedSQLException.class, error -> {
            assertThat((Object) error.getSQLException()).isNotNull();
            assertThat(error.getSQLException().getErrorCode()).isEqualTo(3819);
            assertThat(error.getSQLException().getMessage()).contains(constraint);
        });
    }

    private User user() { var user = new User("ACTIVE"); users.insert(user); return user; }
    private Guest guest() { var guest = new Guest(UUID.randomUUID()); guests.insert(guest); return guest; }
    private FamilyProfile profile() {
        var profile = new FamilyProfile(new ProfileOwnership(user().getId(), null), "가족");
        profiles.insert(profile); return profile;
    }
    private StoredTourismPlace place() {
        var place = new StoredTourismPlace("2847726", "12", "회귀 테스트 장소", "50", "110",
                new Coordinates(new BigDecimal("126.3584772018"), new BigDecimal("33.4394340828")),
                Instant.parse("2026-09-08T03:04:05.123456Z"));
        places.insert(place); return place;
    }
}
