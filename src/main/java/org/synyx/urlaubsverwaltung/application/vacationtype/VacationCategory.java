package org.synyx.urlaubsverwaltung.application.vacationtype;

/**
 * Defines the category of vacation, describes for example if vacation days are used for this kind of vacation or not.
 */
public enum VacationCategory {

    HOLIDAY,
    SPECIALLEAVE,
    UNPAIDLEAVE,
    OVERTIME,
    OTHER,
    /**
     * Part time without fixed day off (LI fork): uses the part time free days quota instead of vacation days.
     */
    PARTTIME_FREEDAY
}
