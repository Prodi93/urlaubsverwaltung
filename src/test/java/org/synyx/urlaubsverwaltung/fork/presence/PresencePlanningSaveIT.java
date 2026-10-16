package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.synyx.urlaubsverwaltung.SingleTenantTestContainersBase;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.account.AccountInteractionService;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.workingtime.WorkingTimeWriteService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.synyx.urlaubsverwaltung.person.Role.USER;

/**
 * Saves the month planning like the browser does: all selects of all days of the month are submitted.
 */
@SpringBootTest
@Transactional
class PresencePlanningSaveIT extends SingleTenantTestContainersBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private WorkingTimeWriteService workingTimeWriteService;
    @Autowired
    private AccountInteractionService accountInteractionService;
    @Autowired
    private PresenceService presenceService;

    @MockitoSpyBean
    private PersonService personService;

    @Test
    void ensureMonthPlanningCanBeSaved() throws Exception {
        final Person person = personService.create("planning", "Marlene", "Muster", "planning@example.org", List.of(), List.of(USER));
        doReturn(person).when(personService).getSignedInUser();
        workingTimeWriteService.touch(List.of(1, 2, 3, 4, 5), LocalDate.of(2026, 1, 1), person);
        accountInteractionService.updateOrCreateHolidaysAccount(person, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null,
            new BigDecimal("25"), new BigDecimal("25"), BigDecimal.ZERO, BigDecimal.ZERO, null);

        final MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        mockMvc.perform(authenticated(get("/web/presence/persons/" + person.getId()).param("year", "2026").param("month", "12")))
            .andExpect(status().isOk());

        final MockHttpServletRequestBuilder save = post("/web/presence/persons/" + person.getId())
            .param("year", "2026")
            .param("month", "12");
        // like a browser: one value per select of every working day
        final Map<String, String> fields = new LinkedHashMap<>();
        for (LocalDate date : new DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))) {
            if (date.getDayOfWeek().getValue() <= 5) {
                fields.put("day." + date, "");
                fields.put("day." + date + ".morning", "");
                fields.put("day." + date + ".noon", "");
            }
        }
        fields.put("day.2026-12-01", "P:OFFICE");
        fields.put("day.2026-12-02", "SPLIT");
        fields.put("day.2026-12-02.morning", "P:OFFICE");
        fields.put("day.2026-12-02.noon", "P:HOME_OFFICE");
        fields.forEach(save::param);

        final MvcResult result = mockMvc.perform(authenticated(save.with(csrf())))
            .andExpect(status().is3xxRedirection())
            .andReturn();

        mockMvc.perform(authenticated(get(result.getResponse().getRedirectedUrl()))
                .flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk());

        assertThat(presenceService.getPresences(person, new DateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2))))
            .containsEntry(LocalDate.of(2026, 12, 1), new Presence(PresenceSlots.fullDay(PresenceType.OFFICE), true))
            .containsEntry(LocalDate.of(2026, 12, 2), new Presence(new PresenceSlots(PresenceType.OFFICE, PresenceType.HOME_OFFICE), true));
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.with(oidcLogin().authorities(new SimpleGrantedAuthority("USER")));
    }
}
