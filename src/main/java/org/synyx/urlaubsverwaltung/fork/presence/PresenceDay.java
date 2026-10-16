package org.synyx.urlaubsverwaltung.fork.presence;

/**
 * What to show for a person on a day of the presence overview.
 *
 * @param kind       what the day is
 * @param type       the presence, only for {@link Kind#PRESENCE}
 * @param explicit   {@code true} if the presence is planned for the day, {@code false} if it is from the weekly pattern
 * @param halfDayOff {@code true} if the person is absent, has a public holiday or does not work half of the day
 */
public record PresenceDay(Kind kind, PresenceType type, boolean explicit, boolean halfDayOff) {

    public enum Kind {
        PRESENCE,
        UNPLANNED,
        ABSENT,
        PUBLIC_HOLIDAY,
        NO_WORKDAY
    }

    static PresenceDay of(Kind kind) {
        return new PresenceDay(kind, null, false, false);
    }

    public boolean isWorkday() {
        return kind == Kind.PRESENCE || kind == Kind.UNPLANNED;
    }
}
