package org.synyx.urlaubsverwaltung.fork.presence;

/**
 * Where a person works on a day. A presence is no absence: it does not need an approval and uses no vacation days.
 */
public enum PresenceType {
    OFFICE,
    HOME_OFFICE,
    MOBILE_WORK
}
