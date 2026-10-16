package org.synyx.urlaubsverwaltung.fork.parttime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.search.PersonSearchUiFragmentSupplier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class PartTimeViewControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PersonService personService;
    @Mock
    private PartTimeService partTimeService;
    @Mock
    private PartTimeFreeDaysService partTimeFreeDaysService;
    @Mock
    private PartTimePermissionEvaluator permissionEvaluator;
    @Mock
    private PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;

    private MockMvc mockMvc;
    private Person person;
    private Person signedInUser;

    @BeforeEach
    void setUp() {
        final PartTimeViewController sut = new PartTimeViewController(personService, partTimeService, partTimeFreeDaysService,
            permissionEvaluator, personSearchUiFragmentSupplier, CLOCK);
        mockMvc = standaloneSetup(sut).build();

        person = new Person("muster", "Muster", "Marlene", "muster@example.org");
        person.setId(1L);
        signedInUser = new Person("office", "Office", "Olaf", "office@example.org");
        signedInUser.setId(2L);
    }

    @Test
    void ensureShowsPartTimeOfCurrentYear() throws Exception {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
        when(personService.getSignedInUser()).thenReturn(signedInUser);
        when(permissionEvaluator.isAllowedToView(signedInUser, person)).thenReturn(true);
        when(permissionEvaluator.isAllowedToEdit(signedInUser)).thenReturn(true);
        when(partTimeService.getPartTimes(person)).thenReturn(List.of());
        when(partTimeFreeDaysService.getSummary(person, 2026)).thenReturn(summary());

        mockMvc.perform(get("/web/person/1/parttime"))
            .andExpect(status().isOk())
            .andExpect(view().name("fork/parttime/parttime"))
            .andExpect(model().attribute("selectedYear", 2026))
            .andExpect(model().attribute("canEdit", true))
            .andExpect(model().attributeExists("summary", "partTimes", "partTimeForm", "quotaForm"));
    }

    @Test
    void ensureAccessDeniedIfNotAllowedToView() {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
        when(personService.getSignedInUser()).thenReturn(signedInUser);
        when(permissionEvaluator.isAllowedToView(signedInUser, person)).thenReturn(false);

        assertThatThrownBy(() -> mockMvc.perform(get("/web/person/1/parttime")))
            .hasCauseInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ensureSavesPartTime() throws Exception {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));

        mockMvc.perform(post("/web/person/1/parttime")
                .param("validFrom", "2026-01-01")
                .param("percentage", "80"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/person/1/parttime?year=2026"))
            .andExpect(flash().attribute("partTimeSaved", true));

        verify(partTimeService).save(person, LocalDate.of(2026, 1, 1), new BigDecimal("80.00"));
    }

    @Test
    void ensureRejectsInvalidPercentage() throws Exception {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
        when(personService.getSignedInUser()).thenReturn(signedInUser);
        when(partTimeFreeDaysService.getSummary(any(), anyInt())).thenReturn(summary());

        mockMvc.perform(post("/web/person/1/parttime")
                .param("validFrom", "2026-01-01")
                .param("percentage", "120"))
            .andExpect(status().isOk())
            .andExpect(view().name("fork/parttime/parttime"))
            .andExpect(model().attributeHasFieldErrors("partTimeForm", "percentage"));

        verify(partTimeService, never()).save(any(), any(), any());
    }

    @Test
    void ensureSavesQuotaOverride() throws Exception {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));

        mockMvc.perform(post("/web/person/1/parttime/quota/2026")
                .param("overrideDays", "40.5")
                .param("comment", "Eintritt"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/web/person/1/parttime?year=2026"));

        verify(partTimeFreeDaysService).setOverride(person, 2026, Optional.of(new BigDecimal("40.50")), Optional.of("Eintritt"));
    }

    @Test
    void ensureRejectsQuotaOverrideWhichIsNoHalfDay() throws Exception {
        when(personService.getPersonByID(1L)).thenReturn(Optional.of(person));
        when(personService.getSignedInUser()).thenReturn(signedInUser);
        when(partTimeFreeDaysService.getSummary(any(), anyInt())).thenReturn(summary());

        mockMvc.perform(post("/web/person/1/parttime/quota/2026")
                .param("overrideDays", "40.3"))
            .andExpect(status().isOk())
            .andExpect(model().attributeHasFieldErrors("quotaForm", "overrideDays"));

        verify(partTimeFreeDaysService, never()).setOverride(any(), anyInt(), any(), any());
    }

    @Test
    void ensurePersonSearchLinksToPartTime() {
        final PartTimeViewController sut = new PartTimeViewController(personService, partTimeService, partTimeFreeDaysService,
            permissionEvaluator, personSearchUiFragmentSupplier, CLOCK);
        assertThat(sut.personSearchUiFragmentSupplier()).isSameAs(personSearchUiFragmentSupplier);
    }

    private static PartTimeFreeDaysSummary summary() {
        return new PartTimeFreeDaysSummary(2026, new BigDecimal("52.0"), Optional.empty(), Optional.empty(), BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
