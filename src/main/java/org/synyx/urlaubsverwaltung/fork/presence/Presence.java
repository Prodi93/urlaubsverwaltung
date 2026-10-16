package org.synyx.urlaubsverwaltung.fork.presence;

/**
 * Presence of a person on a day.
 *
 * @param type     where the person works
 * @param explicit {@code true} if planned for the day, {@code false} if derived from the weekly pattern
 */
public record Presence(PresenceType type, boolean explicit) {
}
