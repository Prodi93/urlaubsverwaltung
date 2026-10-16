package org.synyx.urlaubsverwaltung.fork.teams;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.security.oauth2.core.oidc.IdTokenClaimNames.SUB;

/**
 * Teams single sign on: the tab gets a token of Entra ID from Teams ({@code authentication.getAuthToken()}) and sends
 * it here. A valid token signs in the person like the oidc login: by the subject, else by the email address. Only
 * persons that already exist are signed in, new persons sign in once in the browser.
 */
@RestController
class TeamsSsoController {

    private static final Logger LOG = getLogger(lookup().lookupClass());
    private static final String BEARER = "Bearer ";

    private final TeamsTokenValidator tokenValidator;
    private final PersonService personService;
    private final SecurityContextRepository securityContextRepository;

    TeamsSsoController(TeamsTokenValidator tokenValidator, PersonService personService, SecurityContextRepository securityContextRepository) {
        this.tokenValidator = tokenValidator;
        this.personService = personService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/fork/teams/sso")
    ResponseEntity<Map<String, String>> signIn(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               HttpServletRequest request, HttpServletResponse response) {
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return ResponseEntity.status(401).body(Map.of("error", "missing_token"));
        }

        final Jwt jwt;
        try {
            jwt = tokenValidator.validate(authorization.substring(BEARER.length()));
        } catch (JwtException | IllegalStateException e) {
            LOG.info("Teams single sign on rejected: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "invalid_token"));
        }

        final Optional<Person> maybePerson = resolvePerson(jwt);
        if (maybePerson.isEmpty()) {
            LOG.info("Teams single sign on: no person for subject {}", jwt.getSubject());
            return ResponseEntity.status(403).body(Map.of("error", "unknown_person"));
        }
        final Person person = maybePerson.get();
        if (person.isInactive()) {
            return ResponseEntity.status(403).body(Map.of("error", "inactive_person"));
        }

        final List<SimpleGrantedAuthority> authorities = person.getPermissions().stream()
            .map(role -> new SimpleGrantedAuthority(role.name()))
            .toList();

        // the principal has the name of the person, like after the oidc login
        final Map<String, Object> claims = new HashMap<>(jwt.getClaims());
        claims.put(SUB, person.getUsername());
        final OidcIdToken idToken = new OidcIdToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), claims);
        final DefaultOidcUser user = new DefaultOidcUser(authorities, idToken, SUB);

        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(user, authorities, TeamsSsoSettings.REGISTRATION_ID));
        SecurityContextHolder.setContext(context);

        // new session id against session fixation
        request.getSession(true);
        request.changeSessionId();
        securityContextRepository.saveContext(context, request, response);

        LOG.info("Teams single sign on of person with id {}", person.getId());
        return ResponseEntity.noContent().build();
    }

    private Optional<Person> resolvePerson(Jwt jwt) {
        return Optional.ofNullable(jwt.getSubject())
            .flatMap(personService::getPersonByUsername)
            .or(() -> Stream.of("email", "preferred_username", "upn")
                .map(jwt::getClaimAsString)
                .filter(value -> value != null && value.contains("@"))
                .map(personService::getPersonByMailAddress)
                .flatMap(Optional::stream)
                .findFirst());
    }
}
