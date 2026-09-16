package kr.hankkitravel.trip;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class V11UpgradeTest {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4")
            .withDatabaseName("upgrade_test").withUsername("upgrade_test").withPassword(UUID.randomUUID().toString());
    @Test void upgradesExistingV10DraftWithoutInventingTravelDatesOrLosingRows() throws Exception {
        var old=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                .locations("classpath:db/migration").target("10").cleanDisabled(true).load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(10);
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()); var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO guests(id,public_id) VALUES(1,'00000000-0000-0000-0000-000000000001')");
            s.executeUpdate("INSERT INTO family_profiles(id,owner_guest_id,name) VALUES(1,1,'legacy profile')");
            s.executeUpdate("INSERT INTO trips(id,profile_id,status) VALUES(1,1,'DRAFT')");
            var current=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                    .locations("classpath:db/migration").target("11").cleanDisabled(true).load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(current.validateWithResult().validationSuccessful).isTrue();
            try(var r=s.executeQuery("SELECT profile_id,status,public_id,guest_id,start_date,end_date FROM trips WHERE id=1")) {
                assertThat(r.next()).isTrue(); assertThat(r.getLong(1)).isEqualTo(1); assertThat(r.getString(2)).isEqualTo("DRAFT");
                for(int i=3;i<=6;i++) assertThat(r.getObject(i)).isNull();
            }
            assertThatThrownBy(()->s.executeUpdate("UPDATE trips SET guest_id=1 WHERE id=1")).isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO trips(profile_id,public_id,guest_id,region_key,start_date,end_date) VALUES(1,'00000000-0000-0000-0000-000000000002',1,'JEJU','2026-09-20','2026-09-24')"))
                    .isInstanceOf(java.sql.SQLException.class);
            var latest=Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword())
                    .locations("classpath:db/migration").cleanDisabled(true).load();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(latest.info().current().getVersion().toString()).isEqualTo("12");
            assertThat(latest.migrate().migrationsExecuted).isZero();
        }
    }
}
