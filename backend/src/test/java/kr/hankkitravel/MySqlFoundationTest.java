package kr.hankkitravel;
import static org.assertj.core.api.Assertions.assertThat;
import kr.hankkitravel.foundation.persistence.FoundationMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("mysql")
@SpringBootTest
class MySqlFoundationTest {
    @Autowired FoundationMapper mapper;
    @Autowired Flyway flyway;
    @Test void realMySqlMigrationAndRepeatAreValid() {
        assertThat(mapper.findVersion()).isEqualTo("P0.1");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
