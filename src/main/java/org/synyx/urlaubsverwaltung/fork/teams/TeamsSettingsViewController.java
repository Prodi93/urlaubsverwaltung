package org.synyx.urlaubsverwaltung.fork.teams;

import de.focus_shift.launchpad.api.HasLaunchpad;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.synyx.urlaubsverwaltung.search.HasPersonSearch;
import org.synyx.urlaubsverwaltung.search.PersonSearchUiFragmentSupplier;
import org.synyx.urlaubsverwaltung.search.PersonSuggestionUrlStrategy;

import java.util.Locale;

import static org.synyx.urlaubsverwaltung.security.SecurityRules.IS_OFFICE;

/**
 * Office page with the instructions and the app package for Microsoft Teams.
 */
@Controller
@RequestMapping("/web/settings/teams")
@PreAuthorize(IS_OFFICE)
class TeamsSettingsViewController implements HasLaunchpad, HasPersonSearch {

    private final TeamsAppManifestService manifestService;
    private final TeamsProperties teamsProperties;
    private final TeamsSsoSettings ssoSettings;
    private final PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;

    TeamsSettingsViewController(TeamsAppManifestService manifestService, TeamsProperties teamsProperties, TeamsSsoSettings ssoSettings,
                                PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier) {
        this.manifestService = manifestService;
        this.teamsProperties = teamsProperties;
        this.ssoSettings = ssoSettings;
        this.personSearchUiFragmentSupplier = personSearchUiFragmentSupplier;
    }

    @Override
    public PersonSuggestionUrlStrategy personSuggestionUrlStrategy() {
        return (suggestion, request) -> "/web/person/%s/overview".formatted(suggestion.getId());
    }

    @Override
    public PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier() {
        return personSearchUiFragmentSupplier;
    }

    @GetMapping
    String teams(Model model) {
        model.addAttribute("teamsEnabled", teamsProperties.enabled());
        model.addAttribute("applicationUrl", manifestService.applicationUrl());
        model.addAttribute("appId", manifestService.appId());
        model.addAttribute("ssoAvailable", ssoSettings.isAvailable());
        model.addAttribute("ssoClientId", ssoSettings.clientId().orElse(null));
        model.addAttribute("ssoApplicationIdUri", ssoSettings.applicationIdUri().orElse(null));
        return "fork/teams/settings";
    }

    @GetMapping("/app-package")
    ResponseEntity<byte[]> appPackage(Locale locale) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("urlaubsverwaltung-teams.zip").build());
        return ResponseEntity.ok().headers(headers).body(manifestService.appPackage(locale, ssoSettings.clientId(), ssoSettings.applicationIdUri()));
    }
}
