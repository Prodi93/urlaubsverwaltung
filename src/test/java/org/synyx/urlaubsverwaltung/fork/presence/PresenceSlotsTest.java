package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.HOME_OFFICE;
import static org.synyx.urlaubsverwaltung.fork.presence.PresenceType.OFFICE;

class PresenceSlotsTest {

    @Test
    void ensureFullDayIsCompatibleWithSingleType() {
        assertThat(PresenceSlots.parse("OFFICE")).isEqualTo(PresenceSlots.fullDay(OFFICE));
        assertThat(PresenceSlots.fullDay(OFFICE).asString()).isEqualTo("OFFICE");
        assertThat(PresenceSlots.fullDay(OFFICE).isFullDay()).isTrue();
    }

    @Test
    void ensureHalfDaysRoundTrip() {
        for (PresenceSlots slots : new PresenceSlots[]{new PresenceSlots(OFFICE, HOME_OFFICE), new PresenceSlots(OFFICE, null), new PresenceSlots(null, HOME_OFFICE)}) {
            assertThat(PresenceSlots.parse(slots.asString())).isEqualTo(slots);
        }
        assertThat(new PresenceSlots(OFFICE, HOME_OFFICE).asString()).isEqualTo("OFFICE|HOME_OFFICE");
        assertThat(new PresenceSlots(null, HOME_OFFICE).asString()).isEqualTo("|HOME_OFFICE");
    }

    @Test
    void ensureAtLeastOneHalf() {
        assertThat(PresenceSlots.of(null, null)).isEmpty();
        assertThatThrownBy(() -> new PresenceSlots(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PresenceSlots.parse("OFFICE|HOME|X")).isInstanceOf(IllegalArgumentException.class);
    }
}
