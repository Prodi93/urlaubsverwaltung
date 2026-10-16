package org.synyx.urlaubsverwaltung.fork.presence;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AbsenceOverviewRedirectInterceptorTest {

    @Test
    void ensureRedirectsToTeamCalendarWithYearAndMonth() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/web/absences");
        request.setParameter("year", "2026");
        request.setParameter("month", "10");
        request.setParameter("department", "x");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean proceed = new AbsenceOverviewRedirectInterceptor(true).preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/web/presence?year=2026&month=10");
    }

    @Test
    void ensureIgnoresInvalidMonthAndCanBeDisabled() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/web/absences");
        request.setParameter("month", "october");
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new AbsenceOverviewRedirectInterceptor(true).preHandle(request, response, new Object());
        assertThat(response.getRedirectedUrl()).isEqualTo("/web/presence");

        assertThat(new AbsenceOverviewRedirectInterceptor(false).preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
