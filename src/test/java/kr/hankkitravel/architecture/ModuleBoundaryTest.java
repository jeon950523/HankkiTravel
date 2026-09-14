package kr.hankkitravel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {
    private static final String ROOT = "kr.hankkitravel.";
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "shared", Set.of(), "foundation", Set.of("shared"),
            "identity", Set.of("shared"), "profile", Set.of("shared", "identity"),
            "tourism", Set.of("shared"), "restaurant", Set.of("shared", "tourism"),
            "nutrition", Set.of("shared", "tourism"), "transit", Set.of("shared"),
            "trip", Set.of("shared", "profile"));

    private static String module(JavaClass type) {
        if (!type.getPackageName().startsWith(ROOT)) return "";
        return type.getPackageName().substring(ROOT.length()).split("\\.")[0];
    }

    private static boolean infrastructure(JavaClass type) {
        return type.getPackageName().matches(".*\\.(adapter|persistence|config|infrastructure)(\\..*)?");
    }

    private static final ArchRule BOUNDARIES = classes().should(new ArchCondition<>("follow explicit module boundaries") {
        @Override public void check(JavaClass origin, ConditionEvents events) {
            String source = module(origin);
            if (source.isEmpty()) return;
            if (!ALLOWED.containsKey(source)) {
                events.add(SimpleConditionEvent.violated(origin, "Undefined module: " + source));
                return;
            }
            for (var dependency : origin.getDirectDependenciesFromSelf()) {
                var target = dependency.getTargetClass();
                String destination = module(target);
                if (destination.isEmpty()) continue;
                boolean cross = !source.equals(destination);
                boolean forbidden = cross && (!ALLOWED.get(source).contains(destination) || infrastructure(target));
                forbidden |= origin.getPackageName().contains(".model") && infrastructure(target);
                forbidden |= cross && source.equals("trip") && destination.equals("profile")
                        && !target.getName().equals("kr.hankkitravel.profile.model.FamilyProfileId");
                forbidden |= origin.getSimpleName().endsWith("Controller") && target.getSimpleName().endsWith("Mapper");
                if (forbidden) events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
            }
        }
    });

    @Test void productionModulesRespectAllowedEdgesAndInfrastructureOwnership() {
        var production = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("kr.hankkitravel");
        BOUNDARIES.check(production);
        assertThat(production.stream().map(ModuleBoundaryTest::module).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet()))
                .contains("shared", "foundation", "identity", "profile", "tourism", "restaurant", "nutrition", "transit", "trip");
    }

    @Test void noModuleCycles() {
        slices().matching("kr.hankkitravel.(*)..").should().beFreeOfCycles()
                .check(new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("kr.hankkitravel"));
    }

    @Test void allowedCrossModuleValueObjectsReallyPass() {
        BOUNDARIES.check(new ClassFileImporter().importClasses(
                kr.hankkitravel.restaurant.model.Restaurant.class,
                kr.hankkitravel.profile.model.FamilyProfile.class,
                kr.hankkitravel.trip.model.Trip.class));
    }

    @Test void forbiddenDependenciesReallyFail() {
        for (Class<?> bad : new Class<?>[] {
                kr.hankkitravel.identity.fixture.ForbiddenTourism.class,
                kr.hankkitravel.shared.fixture.ForbiddenProduct.class,
                kr.hankkitravel.restaurant.fixture.ForbiddenInfrastructure.class,
                kr.hankkitravel.trip.fixture.ForbiddenAggregate.class,
                kr.hankkitravel.profile.model.fixture.ForbiddenPersistence.class,
                kr.hankkitravel.profile.fixture.ForbiddenController.class,
                kr.hankkitravel.unregistered.fixture.ForbiddenModule.class }) {
            assertThatThrownBy(() -> BOUNDARIES.check(new ClassFileImporter().importClasses(bad)))
                    .as(bad.getSimpleName()).isInstanceOf(AssertionError.class);
        }
    }

    @Test void nutritionHasAnExplicitLiveContract() throws Exception {
        assertThat(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/hankkitravel/nutrition/package-info.java")))
                .contains("런타임", "영속");
    }

    @Test void eachMapperAccessesOnlyItsOwnedTables() throws Exception {
        var ownership = Map.ofEntries(
                Map.entry("foundation_metadata", "foundation"), Map.entry("users", "identity"),
                Map.entry("guests", "identity"), Map.entry("family_profiles", "profile"),
                Map.entry("family_members", "profile"), Map.entry("tourism_places", "tourism"),
                Map.entry("tourism_sync_runs", "tourism"), Map.entry("tourism_sync_scope_states", "tourism"),
                Map.entry("restaurants", "restaurant"), Map.entry("trips", "trip"),
                Map.entry("nutrition_foods", "nutrition"), Map.entry("nutrition_import_runs", "nutrition"));
        var tablePattern = java.util.regex.Pattern.compile("(?i)\\b(?:FROM|INTO|UPDATE|JOIN)\\s+([a-z_]+)");
        var production = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("kr.hankkitravel");
        int mapperCount = 0;
        for (var type : production) {
            if (!type.isAnnotatedWith(org.apache.ibatis.annotations.Mapper.class)) continue;
            mapperCount++;
            for (var method : Class.forName(type.getName()).getDeclaredMethods()) {
                String[] sql = method.isAnnotationPresent(org.apache.ibatis.annotations.Insert.class)
                        ? method.getAnnotation(org.apache.ibatis.annotations.Insert.class).value()
                        : method.isAnnotationPresent(org.apache.ibatis.annotations.Select.class)
                        ? method.getAnnotation(org.apache.ibatis.annotations.Select.class).value()
                        : method.isAnnotationPresent(org.apache.ibatis.annotations.Update.class)
                        ? method.getAnnotation(org.apache.ibatis.annotations.Update.class).value()
                        : method.isAnnotationPresent(org.apache.ibatis.annotations.Delete.class)
                        ? method.getAnnotation(org.apache.ibatis.annotations.Delete.class).value() : new String[0];
                assertThat(sql).as(method.toString()).isNotEmpty();
                var statement = String.join(" ", sql).replaceAll("(?i)ON DUPLICATE KEY UPDATE\\s+", "");
                var matcher = tablePattern.matcher(statement);
                boolean found = false;
                while (matcher.find()) {
                    found = true;
                    assertThat(ownership.get(matcher.group(1).toLowerCase(java.util.Locale.ROOT)))
                            .as(type.getName() + " table ownership").isEqualTo(module(type));
                }
                assertThat(found).as(method.toString()).isTrue();
            }
        }
        assertThat(mapperCount).isEqualTo(14);
    }
}
