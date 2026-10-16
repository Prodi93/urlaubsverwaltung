package org.synyx.urlaubsverwaltung.fork.presence;

/**
 * Presence of a person on a day.
 *
 * @param slots    where the person works in the morning and in the noon
 * @param explicit {@code true} if planned for the day, {@code false} if derived from the weekly pattern
 */
public record Presence(PresenceSlots slots, boolean explicit) {
}
