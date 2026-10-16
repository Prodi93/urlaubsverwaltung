package org.synyx.urlaubsverwaltung.fork.publicholiday;

import de.focus_shift.jollyday.core.Holiday;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static de.focus_shift.jollyday.core.HolidayType.PUBLIC_HOLIDAY;
import static java.time.Month.JANUARY;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.LIECHTENSTEIN_BANK_HOLIDAYS;

/**
 * Bank holidays of Liechtenstein that are not statutory public holidays (see e.g. https://llb.li/de/kontakt/bankfeiertage).
 * The statutory ones are provided by jollyday, christmas eve and new years eve are handled generically.
 */
@Component
class LiechtensteinBankHolidaysProvider implements AdditionalPublicHolidaysProvider {

    static final String BERCHTOLDSTAG = "LI_BERCHTOLDSTAG";
    static final String FASNACHTSDIENSTAG = "LI_FASNACHTSDIENSTAG";
    static final String KARFREITAG = "LI_KARFREITAG";

    private static final Set<String> PROPERTIES_KEYS = Set.of(BERCHTOLDSTAG, FASNACHTSDIENSTAG, KARFREITAG);
    private static final String MESSAGE_KEY_PREFIX = "fork.publicholiday.description.";

    private final MessageSource messageSource;

    LiechtensteinBankHolidaysProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public Set<Holiday> getHolidays(LocalDate from, LocalDate to, FederalState federalState) {
        if (federalState != LIECHTENSTEIN_BANK_HOLIDAYS || from.isAfter(to)) {
            return Set.of();
        }

        final Set<Holiday> holidays = new HashSet<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            final LocalDate easterSunday = EasterSunday.of(year);
            addIfInRange(holidays, LocalDate.of(year, JANUARY, 2), BERCHTOLDSTAG, from, to);
            addIfInRange(holidays, easterSunday.minusDays(47), FASNACHTSDIENSTAG, from, to);
            addIfInRange(holidays, easterSunday.minusDays(2), KARFREITAG, from, to);
        }
        return holidays;
    }

    @Override
    public Optional<String> getDescription(Holiday holiday, Locale locale) {
        final String propertiesKey = holiday.getPropertiesKey();
        if (!PROPERTIES_KEYS.contains(propertiesKey)) {
            return Optional.empty();
        }
        return Optional.of(messageSource.getMessage(MESSAGE_KEY_PREFIX + propertiesKey, null, propertiesKey, locale));
    }

    private static void addIfInRange(Set<Holiday> holidays, LocalDate date, String propertiesKey, LocalDate from, LocalDate to) {
        if (!date.isBefore(from) && !date.isAfter(to)) {
            holidays.add(new Holiday(date, propertiesKey, PUBLIC_HOLIDAY));
        }
    }
}
