package org.synyx.urlaubsverwaltung.fork.presence;

import java.util.Optional;

/**
 * Presence of a day, separately for the morning and the noon (afternoon). A half may be empty, but not both.
 */
public record PresenceSlots(PresenceType morning, PresenceType noon) {

    private static final String SEPARATOR = "|";

    public PresenceSlots {
        if (morning == null && noon == null) {
            throw new IllegalArgumentException("a presence needs at least a morning or a noon");
        }
    }

    public static PresenceSlots fullDay(PresenceType type) {
        return new PresenceSlots(type, type);
    }

    /**
     * @return slots of the given halves, empty if both are empty
     */
    public static Optional<PresenceSlots> of(PresenceType morning, PresenceType noon) {
        return morning == null && noon == null ? Optional.empty() : Optional.of(new PresenceSlots(morning, noon));
    }

    public boolean isFullDay() {
        return morning == noon;
    }

    /**
     * @return {@code "OFFICE"} for a full day, {@code "OFFICE|HOME_OFFICE"}, {@code "OFFICE|"} or {@code "|OFFICE"} otherwise
     */
    public String asString() {
        if (isFullDay()) {
            return morning.name();
        }
        return (morning == null ? "" : morning.name()) + SEPARATOR + (noon == null ? "" : noon.name());
    }

    public static PresenceSlots parse(String value) {
        final String trimmed = value.strip();
        if (!trimmed.contains(SEPARATOR)) {
            return fullDay(PresenceType.valueOf(trimmed));
        }
        final String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid presence: " + value);
        }
        return new PresenceSlots(type(parts[0]), type(parts[1]));
    }

    private static PresenceType type(String value) {
        return value.isBlank() ? null : PresenceType.valueOf(value.strip());
    }
}
