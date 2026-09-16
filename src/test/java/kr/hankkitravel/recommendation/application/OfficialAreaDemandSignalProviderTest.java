package kr.hankkitravel.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import kr.hankkitravel.shared.integration.IntegrationException;
import kr.hankkitravel.shared.integration.IntegrationFailure;
import org.junit.jupiter.api.Test;

class OfficialAreaDemandSignalProviderTest {
    private static final AreaDemandSignalProvider.RegionKey REGION =
            new AreaDemandSignalProvider.RegionKey("50", "11", "제주특별자치도 제주시");

    @Test void combinesBothOfficialFamiliesAtEqualWeight() {
        var provider = provider(strength("80", "60"), resource("40", "20"));
        var signal = provider.signal(REGION, LocalDate.of(2026, 9, 17));
        assertThat(signal.evaluated()).isTrue();
        assertThat(signal.score()).isEqualTo(50);
        assertThat(signal.referencePeriod()).isEqualTo("202608");
        assertThat(signal.demandStrengthEvaluated()).isTrue();
        assertThat(signal.resourceDemandEvaluated()).isTrue();
    }

    @Test void usesOnlyAvailableStrengthWithoutZeroPenalty() {
        var signal = provider(strength("80", "60"), failingResource()).signal(REGION, LocalDate.of(2026,9,17));
        assertThat(signal.score()).isEqualTo(70);
        assertThat(signal.evaluated()).isTrue();
        assertThat(signal.resourceDemandEvaluated()).isFalse();
    }

    @Test void usesOnlyAvailableResourceWithoutZeroPenalty() {
        var signal = provider(failingStrength(), resource("40", "20")).signal(REGION, LocalDate.of(2026,9,17));
        assertThat(signal.score()).isEqualTo(30);
        assertThat(signal.evaluated()).isTrue();
        assertThat(signal.demandStrengthEvaluated()).isFalse();
    }

    @Test void bothFailuresRemainNotEvaluatedAndOutOfContractScaleIsRejected() {
        assertThat(provider(failingStrength(), failingResource()).signal(REGION, LocalDate.of(2026,9,17)).evaluated()).isFalse();
        assertThat(OfficialAreaDemandSignalProvider.normalize(new BigDecimal("100.01"))).isNull();
        assertThat(OfficialAreaDemandSignalProvider.normalize(new BigDecimal("72.01"))).isEqualTo(72);
    }

    private OfficialAreaDemandSignalProvider provider(TourismDemandStrengthSource strength, TourismResourceDemandSource resource) {
        return new OfficialAreaDemandSignalProvider(strength,resource,"");
    }
    private TourismDemandStrengthSource strength(String stay,String consumption) {
        return (a,d,p)->new TourismDemandStrengthSource.Evidence(new BigDecimal(stay),new BigDecimal(consumption));
    }
    private TourismResourceDemandSource resource(String service,String culture) {
        return (a,d,p)->new TourismResourceDemandSource.Evidence(new BigDecimal(service),new BigDecimal(culture));
    }
    private TourismDemandStrengthSource failingStrength() {
        return (a,d,p)->{throw new IntegrationException("test",IntegrationFailure.NETWORK_FAILURE);};
    }
    private TourismResourceDemandSource failingResource() {
        return (a,d,p)->{throw new IntegrationException("test",IntegrationFailure.NETWORK_FAILURE);};
    }
}
