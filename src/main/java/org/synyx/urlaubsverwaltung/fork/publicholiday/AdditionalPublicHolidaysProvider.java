package org.synyx.urlaubsverwaltung.fork.publicholiday;

import de.focus_shift.jollyday.core.Holiday;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Provides public holidays in addition to the ones of jollyday, e.g. bank holidays of a region.
 */
public interface AdditionalPublicHolidaysProvider {

    /**
     * @return additional public holidays between from and to (both inclusive) for the given federal state,
     * empty if the provider is not responsible for the federal state
     */
    Set<Holiday> getHolidays(LocalDate from, LocalDate to, FederalState federalState);

    /**
     * @return the localized description of a holiday created by this provider, empty for holidays of others
     */
    Optional<String> getDescription(Holiday holiday, Locale locale);

    default boolean isHoliday(LocalDate date, FederalState federalState) {
        return !getHolidays(date, date, federalState).isEmpty();
    }
}
