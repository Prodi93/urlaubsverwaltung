package org.synyx.urlaubsverwaltung.fork.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.api.RestControllerAdviceMarker;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.synyx.urlaubsverwaltung.security.SecurityRules.IS_BOSS_OR_OFFICE;

@Tag(name = "presences", description = "Presences: planned presence (office, home office, mobile work) of a person (LI fork)")
@RestControllerAdviceMarker
@RestController
@RequestMapping("/api/persons/{personId}/presences")
class PresenceApiController {

    private static final String IS_ALLOWED_FOR_PERSON = IS_BOSS_OR_OFFICE +
        " or @userApiMethodSecurity.isSamePersonId(authentication, #personId)" +
        " or @userApiMethodSecurity.isInDepartmentOfDepartmentHead(authentication, #personId)" +
        " or @userApiMethodSecurity.isInDepartmentOfSecondStageAuthority(authentication, #personId)";

    private static final long MAX_DAYS = 400;

    private final PresenceService presenceService;
    private final PersonService personService;

    PresenceApiController(PresenceService presenceService, PersonService personService) {
        this.presenceService = presenceService;
        this.personService = personService;
    }

    @Operation(summary = "Get the presences of a person", description = "Planned and weekly pattern presences for the given period (both inclusive)")
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    @PreAuthorize(IS_ALLOWED_FOR_PERSON)
    public List<PresenceDto> presences(@Parameter(description = "ID of the person") @PathVariable("personId") Long personId,
                                       @RequestParam("from") @DateTimeFormat(iso = DATE) LocalDate from,
                                       @RequestParam("to") @DateTimeFormat(iso = DATE) LocalDate to) {
        if (from.isAfter(to) || DAYS.between(from, to) > MAX_DAYS) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid period, 'from' must be before 'to' and at most %s days".formatted(MAX_DAYS));
        }
        return presenceService.getPresences(person(personId), new DateRange(from, to)).entrySet().stream()
            .map(entry -> new PresenceDto(entry.getKey(), entry.getValue().type(), entry.getValue().explicit()))
            .sorted(Comparator.comparing(PresenceDto::date))
            .toList();
    }

    @Operation(summary = "Plan the presence of a person on a day")
    @PutMapping(value = "/{date}", consumes = APPLICATION_JSON_VALUE)
    @PreAuthorize(IS_ALLOWED_FOR_PERSON)
    public ResponseEntity<Void> setPresence(@Parameter(description = "ID of the person") @PathVariable("personId") Long personId,
                                            @PathVariable("date") @DateTimeFormat(iso = DATE) LocalDate date,
                                            @RequestBody PresenceRequestDto request) {
        if (request == null || request.type() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        presenceService.setPresence(person(personId), date, request.type());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove the planned presence of a person on a day", description = "The weekly pattern applies again")
    @DeleteMapping("/{date}")
    @PreAuthorize(IS_ALLOWED_FOR_PERSON)
    public ResponseEntity<Void> deletePresence(@Parameter(description = "ID of the person") @PathVariable("personId") Long personId,
                                               @PathVariable("date") @DateTimeFormat(iso = DATE) LocalDate date) {
        presenceService.setPresence(person(personId), date, null);
        return ResponseEntity.noContent().build();
    }

    private Person person(Long personId) {
        return personService.getPersonByID(personId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "person not found"));
    }

    public record PresenceDto(LocalDate date, PresenceType type, boolean explicit) {
    }

    public record PresenceRequestDto(PresenceType type) {
    }
}
