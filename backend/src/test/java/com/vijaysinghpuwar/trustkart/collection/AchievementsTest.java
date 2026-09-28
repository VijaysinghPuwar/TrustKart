package com.vijaysinghpuwar.trustkart.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementsTest {

    @Test
    void theCatalogHasAtLeastFiftyUniqueAchievementsAllLockedForNewShoppers() {
        List<Achievements.Achievement> all = new Achievements(null, null, null).preview();
        assertThat(all).hasSize(Achievements.count()).hasSizeGreaterThanOrEqualTo(50);
        assertThat(new HashSet<>(all.stream().map(Achievements.Achievement::code).toList())).hasSize(all.size());
        assertThat(all).allSatisfy(a -> {
            assertThat(a.unlocked()).isFalse();
            assertThat(a.percent()).isZero();
        });
        assertThat(all).extracting(Achievements.Achievement::code)
                .contains("SPEND_100M", "SPEND_1B", "SPEND_100B", "FIRST_PURCHASE");
    }

    @Test
    void moneyIsCompactAndNeverRoundsUp() {
        assertThat(Achievements.usd(new BigDecimal("950"))).isEqualTo("$950");
        assertThat(Achievements.usd(new BigDecimal("12599"))).isEqualTo("$12.5K");
        assertThat(Achievements.usd(new BigDecimal("999999.99"))).isEqualTo("$999.9K");
        assertThat(Achievements.usd(new BigDecimal("1000000"))).isEqualTo("$1M");
        assertThat(Achievements.usd(new BigDecimal("100000000000"))).isEqualTo("$100B");
    }
}
