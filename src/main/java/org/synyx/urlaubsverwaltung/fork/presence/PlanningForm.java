package org.synyx.urlaubsverwaltung.fork.presence;

import org.synyx.urlaubsverwaltung.application.vacationtype.VacationType;
import org.synyx.urlaubsverwaltung.period.DayLength;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.period.DayLength.MORNING;
import static org.synyx.urlaubsverwaltung.period.DayLength.NOON;

/**
 * Parameters of the month planning. Per day:
 * <ul>
 *     <li>{@code day.<date>}: the whole day, {@code SPLIT} for separate halves</li>
 *     <li>{@code day.<date>.morning} and {@code day.<date>.noon}: the halves</li>
 * </ul>
 * A value is empty (weekly pattern), {@code P:<PresenceType>} for a presence or {@code A:<vacation type id>} to apply
 * for leave. Consecutive full days with the same vacation type become one application for leave.
 */
final class PlanningForm {

    static final String SPLIT = "SPLIT";
    private static final String PRESENCE_PREFIX = "P:";
    private static final String ABSENCE_PREFIX = "A:";
    private static final String DAY_PREFIX = "day.";

    record AbsenceRequest(Long vacationTypeId, LocalDate from, LocalDate to, DayLength dayLength) {
    }

    private final Map<LocalDate, PresenceSlots> presences;
    private final List<AbsenceRequest> absenceRequests;

    private PlanningForm(Map<LocalDate, PresenceSlots> presences, List<AbsenceRequest> absenceRequests) {
        this.presences = presences;
        this.absenceRequests = absenceRequests;
    }

    /**
     * @return presences per day, {@code null} removes the planned presence of the day
     */
    Map<LocalDate, PresenceSlots> presences() {
        return presences;
    }

    List<AbsenceRequest> absenceRequests() {
        return absenceRequests;
    }

    static String presenceValue(PresenceType type) {
        return PRESENCE_PREFIX + type.name();
    }

    static String absenceValue(VacationType<?> vacationType) {
        return ABSENCE_PREFIX + vacationType.getId();
    }

    /**
     * @param days the days of the planned period, only working halves are taken
     */
    static PlanningForm parse(Map<String, String> parameters, Map<LocalDate, PresenceDay> days) {
        final Map<LocalDate, PresenceSlots> presences = new LinkedHashMap<>();
        final TreeMap<LocalDate, Long> fullDayAbsences = new TreeMap<>();
        final List<AbsenceRequest> requests = new ArrayList<>();

        days.forEach((date, day) -> {
            if (!day.isWorkday()) {
                return;
            }
            final String key = DAY_PREFIX + date;
            final String full = parameters.get(key);
            final boolean halves = full == null || SPLIT.equals(full);
            if (full == null && !parameters.containsKey(key + ".morning") && !parameters.containsKey(key + ".noon")) {
                return;
            }

            final String morning = day.morning().isWorking() ? (halves ? parameters.getOrDefault(key + ".morning", "") : full) : "";
            final String noon = day.noon().isWorking() ? (halves ? parameters.getOrDefault(key + ".noon", "") : full) : "";

            presences.put(date, PresenceSlots.of(presence(morning), presence(noon)).orElse(null));

            final Long morningAbsence = absence(morning);
            final Long noonAbsence = absence(noon);
            if (morningAbsence != null && morningAbsence.equals(noonAbsence)) {
                fullDayAbsences.put(date, morningAbsence);
            } else {
                if (morningAbsence != null) {
                    requests.add(new AbsenceRequest(morningAbsence, date, date, MORNING));
                }
                if (noonAbsence != null) {
                    requests.add(new AbsenceRequest(noonAbsence, date, date, NOON));
                }
            }
        });

        requests.addAll(0, joinFullDays(fullDayAbsences, days));
        return new PlanningForm(presences, requests);
    }

    /**
     * Joins full days with the same vacation type, if there is no other working day between them.
     */
    private static List<AbsenceRequest> joinFullDays(TreeMap<LocalDate, Long> fullDays, Map<LocalDate, PresenceDay> days) {
        final List<AbsenceRequest> requests = new ArrayList<>();
        LocalDate from = null;
        LocalDate to = null;
        Long type = null;
        for (Map.Entry<LocalDate, Long> entry : fullDays.entrySet()) {
            final LocalDate date = entry.getKey();
            if (type != null && type.equals(entry.getValue()) && onlyFreeDaysBetween(to, date, days)) {
                to = date;
                continue;
            }
            if (type != null) {
                requests.add(new AbsenceRequest(type, from, to, FULL));
            }
            from = date;
            to = date;
            type = entry.getValue();
        }
        if (type != null) {
            requests.add(new AbsenceRequest(type, from, to, FULL));
        }
        return requests;
    }

    private static boolean onlyFreeDaysBetween(LocalDate from, LocalDate to, Map<LocalDate, PresenceDay> days) {
        for (LocalDate date = from.plusDays(1); date.isBefore(to); date = date.plusDays(1)) {
            final PresenceDay day = days.get(date);
            if (day == null || day.isWorkday()) {
                return false;
            }
        }
        return true;
    }

    private static PresenceType presence(String value) {
        return value.startsWith(PRESENCE_PREFIX) ? PresenceType.valueOf(value.substring(PRESENCE_PREFIX.length())) : null;
    }

    private static Long absence(String value) {
        return value.startsWith(ABSENCE_PREFIX) ? Long.valueOf(value.substring(ABSENCE_PREFIX.length())) : null;
    }
}
