package org.synyx.urlaubsverwaltung.fork.publicholiday;

import de.focus_shift.jollyday.core.HolidayCalendar;
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.period.DayLength;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHoliday;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHolidaysService;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHolidaysServiceImpl;
import org.synyx.urlaubsverwaltung.publicholiday.PublicHolidaysSettings;
import org.synyx.urlaubsverwaltung.settings.SettingsService;
import org.synyx.urlaubsverwaltung.workingtime.FederalState;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.time.LocalDate.of;
import static java.time.Month.APRIL;
import static java.time.Month.DECEMBER;
import static java.time.Month.FEBRUARY;
import static java.time.Month.JANUARY;
import static java.time.Month.MARCH;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.synyx.urlaubsverwaltung.period.DayLength.FULL;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.LIECHTENSTEIN;
import static org.synyx.urlaubsverwaltung.workingtime.FederalState.LIECHTENSTEIN_BANK_HOLIDAYS;

class LiechtensteinPublicHolidaysTest {

    private PublicHolidaysService sut;
    private Supplier<PublicHolidaysSettings> publicHolidaysSettingsSupplier;

    @BeforeEach
    void setUp() {
        final StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("fork.publicholiday.description.LI_BERCHTOLDSTAG", Locale.GERMAN, "Berchtoldstag");
        messageSource.addMessage("fork.publicholiday.description.LI_FASNACHTSDIENSTAG", Locale.GERMAN, "Fasnachtsdienstag");
        messageSource.addMessage("fork.publicholiday.description.LI_KARFREITAG", Locale.GERMAN, "Karfreitag");

        final HolidayManager liHolidayManager = HolidayManager.getInstance(ManagerParameters.create(HolidayCalendar.LIECHTENSTEIN));
        sut = new PublicHolidaysServiceImpl(mock(SettingsService.class), Map.of("li", liHolidayManager),
            List.of(new LiechtensteinBankHolidaysProvider(messageSource)));

        final PublicHolidaysSettings publicHolidaysSettings = new PublicHolidaysSettings();
        publicHolidaysSettings.setWorkingDurationForChristmasEve(DayLength.MORNING);
        publicHolidaysSettings.setWorkingDurationForNewYearsEve(DayLength.MORNING);
        publicHolidaysSettingsSupplier = () -> publicHolidaysSettings;
    }

    @Test
    void ensureStatutoryPublicHolidaysOfLiechtenstein2026() {
        final List<LocalDate> dates = publicHolidayDates(2026, LIECHTENSTEIN);
        assertThat(dates).containsExactlyInAnyOrder(
            of(2026, 1, 1), of(2026, 1, 6), of(2026, 2, 2), of(2026, 3, 19),
            of(2026, 4, 5), of(2026, 4, 6), of(2026, 5, 1), of(2026, 5, 14),
            of(2026, 5, 24), of(2026, 5, 25), of(2026, 6, 4), of(2026, 8, 15),
            of(2026, 9, 8), of(2026, 11, 1), of(2026, 12, 8), of(2026, 12, 24),
            of(2026, 12, 25), of(2026, 12, 26), of(2026, 12, 31)
        );
    }

    @Test
    void ensureBankHolidaysOfLiechtenstein2026ContainStatutoryAndBankHolidays() {
        final List<LocalDate> statutory = publicHolidayDates(2026, LIECHTENSTEIN);
        final List<LocalDate> bank = publicHolidayDates(2026, LIECHTENSTEIN_BANK_HOLIDAYS);

        assertThat(bank)
            .hasSize(statutory.size() + 3)
            .containsAll(statutory)
            .contains(of(2026, JANUARY, 2), of(2026, FEBRUARY, 17), of(2026, APRIL, 3));
    }

    @ParameterizedTest
    @CsvSource({
        // Berchtoldstag, Fasnachtsdienstag, Karfreitag
        "2025-01-02, 2025-03-04, 2025-04-18",
        "2026-01-02, 2026-02-17, 2026-04-03",
        "2027-01-02, 2027-02-09, 2027-03-26",
    })
    void ensureBankHolidaysOnlyInBankHolidayRegion(LocalDate berchtoldstag, LocalDate fasnachtsdienstag, LocalDate karfreitag) {
        for (LocalDate date : List.of(berchtoldstag, fasnachtsdienstag, karfreitag)) {
            assertThat(sut.getPublicHoliday(date, LIECHTENSTEIN, publicHolidaysSettingsSupplier)).as("statutory %s", date).isEmpty();
            assertThat(sut.getPublicHoliday(date, LIECHTENSTEIN_BANK_HOLIDAYS, publicHolidaysSettingsSupplier)).as("bank %s", date).isPresent();
        }
    }

    @Test
    void ensureBankHolidayIsAFullPublicHolidayWithDescription() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        try {
            final Optional<PublicHoliday> karfreitag = sut.getPublicHoliday(of(2026, APRIL, 3), LIECHTENSTEIN_BANK_HOLIDAYS, publicHolidaysSettingsSupplier);
            assertThat(karfreitag).hasValueSatisfying(publicHoliday -> {
                assertThat(publicHoliday.dayLength()).isEqualTo(FULL);
                assertThat(publicHoliday.description()).isEqualTo("Karfreitag");
            });
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void ensureChristmasEveStillUsesSettingsInBankHolidayRegion() {
        final Optional<PublicHoliday> christmasEve = sut.getPublicHoliday(of(2026, DECEMBER, 24), LIECHTENSTEIN_BANK_HOLIDAYS, publicHolidaysSettingsSupplier);
        assertThat(christmasEve).hasValueSatisfying(publicHoliday -> assertThat(publicHoliday.dayLength()).isEqualTo(DayLength.NOON));
    }

    @Test
    void ensureNoBankHolidayOnNormalWorkday() {
        assertThat(sut.getPublicHoliday(of(2026, MARCH, 10), LIECHTENSTEIN_BANK_HOLIDAYS, publicHolidaysSettingsSupplier)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = FederalState.class, names = {"LIECHTENSTEIN", "LIECHTENSTEIN_BANK_HOLIDAYS"})
    void ensureGetPublicHolidaysOfARangeEqualsGetPublicHolidayForEveryDateOfTheRange(FederalState federalState) {
        for (int year = 2025; year <= 2027; year++) {
            final LocalDate start = of(year, JANUARY, 1);
            final LocalDate end = of(year, DECEMBER, 31);

            final Map<LocalDate, PublicHoliday> publicHolidaysByDate = sut.getPublicHolidays(start, end, federalState, publicHolidaysSettingsSupplier).stream()
                .collect(toMap(PublicHoliday::date, identity()));

            for (LocalDate date : new DateRange(start, end)) {
                final Optional<PublicHoliday> expected = sut.getPublicHoliday(date, federalState, publicHolidaysSettingsSupplier);
                final Optional<PublicHoliday> actual = Optional.ofNullable(publicHolidaysByDate.get(date));

                assertThat(actual.map(PublicHoliday::date)).as("date %s", date).isEqualTo(expected.map(PublicHoliday::date));
                assertThat(actual.map(PublicHoliday::dayLength)).as("day length %s", date).isEqualTo(expected.map(PublicHoliday::dayLength));
            }
        }
    }

    private List<LocalDate> publicHolidayDates(int year, FederalState federalState) {
        return sut.getPublicHolidays(of(year, JANUARY, 1), of(year, DECEMBER, 31), federalState, publicHolidaysSettingsSupplier).stream()
            .map(PublicHoliday::date)
            .toList();
    }
}
