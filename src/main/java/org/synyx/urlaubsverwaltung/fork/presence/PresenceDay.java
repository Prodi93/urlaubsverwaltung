package org.synyx.urlaubsverwaltung.fork.presence;

import java.util.Objects;
import java.util.Optional;

/**
 * What to show for a person on a day of the team calendar, separately for the morning and the noon.
 */
public record PresenceDay(Half morning, Half noon) {

    public enum Kind {
        PRESENCE,
        UNPLANNED,
        ABSENT,
        PUBLIC_HOLIDAY,
        NO_WORKDAY
    }

    /**
     * @param kind     what the half day is
     * @param type     the presence, only for {@link Kind#PRESENCE}
     * @param explicit {@code true} if the presence is planned for the day, {@code false} if it is from the weekly pattern
     * @param absence  the absence, only for {@link Kind#ABSENT}
     */
    public record Half(Kind kind, PresenceType type, boolean explicit, AbsenceMark absence) {

        static Half of(Kind kind) {
            return new Half(kind, null, false, null);
        }

        static Half presence(PresenceType type, boolean explicit) {
            return new Half(Kind.PRESENCE, type, explicit, null);
        }

        static Half absent(AbsenceMark absence) {
            return new Half(Kind.ABSENT, null, false, absence);
        }

        public boolean isWorking() {
            return kind == Kind.PRESENCE || kind == Kind.UNPLANNED;
        }
    }

    /**
     * An absence as shown to the viewer.
     *
     * @param label   vacation type, "sick" or "absent" if the viewer may not see the details
     * @param color   css color of the absence, e.g. {@code var(--absence-color-YELLOW)}
     * @param waiting {@code true} if not yet allowed
     * @param applicationId the application for leave, only if the viewer may see the details
     */
    public record AbsenceMark(String label, String color, boolean waiting, Long applicationId) {
    }

    static PresenceDay fullDay(Half half) {
        return new PresenceDay(half, half);
    }

    /**
     * @return {@code true} if morning and noon differ and have to be shown separately
     */
    public boolean isSplit() {
        return !Objects.equals(morning, noon);
    }

    /**
     * @return the kind of the day, for a split day the kind of the morning if it is no working half, else of the noon
     */
    public Kind kind() {
        return morning.kind();
    }

    public boolean isWorkday() {
        return morning.isWorking() || noon.isWorking();
    }

    public boolean isFullWorkday() {
        return morning.isWorking() && noon.isWorking();
    }

    public boolean isExplicit() {
        return (morning.kind() == Kind.PRESENCE && morning.explicit()) || (noon.kind() == Kind.PRESENCE && noon.explicit());
    }

    /**
     * @return the presence of the working halves, empty if there is none
     */
    public Optional<PresenceSlots> presence() {
        return PresenceSlots.of(morning.type(), noon.type());
    }

    /**
     * @return number of persons in the office for this day: 1, 0.5 or 0
     */
    public double inOffice() {
        return (morning.type() == PresenceType.OFFICE ? 0.5 : 0) + (noon.type() == PresenceType.OFFICE ? 0.5 : 0);
    }
}
