package org.synyx.urlaubsverwaltung.fork.csv;

import de.focus_shift.launchpad.api.HasLaunchpad;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.synyx.urlaubsverwaltung.absence.DateRange;
import org.synyx.urlaubsverwaltung.person.Person;
import org.synyx.urlaubsverwaltung.person.PersonService;
import org.synyx.urlaubsverwaltung.search.HasPersonSearch;
import org.synyx.urlaubsverwaltung.search.PersonSearchUiFragmentSupplier;
import org.synyx.urlaubsverwaltung.search.PersonSuggestionUrlStrategy;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

@Controller
@RequestMapping("/web/import-export")
@PreAuthorize("hasAuthority('USER')")
class CsvImportExportViewController implements HasLaunchpad, HasPersonSearch {

    private static final String VIEW = "fork/csv/import-export";
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", UTF_8);
    private static final long MAX_EXPORT_DAYS = 3660;

    private final CsvImportExportService csvImportExportService;
    private final CsvScopeResolver scopeResolver;
    private final PersonService personService;
    private final PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier;
    private final Clock clock;

    CsvImportExportViewController(CsvImportExportService csvImportExportService, CsvScopeResolver scopeResolver,
                                  PersonService personService, PersonSearchUiFragmentSupplier personSearchUiFragmentSupplier, Clock clock) {
        this.csvImportExportService = csvImportExportService;
        this.scopeResolver = scopeResolver;
        this.personService = personService;
        this.personSearchUiFragmentSupplier = personSearchUiFragmentSupplier;
        this.clock = clock;
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
    String importExport(Model model) {
        fillModel(model, personService.getSignedInUser());
        return VIEW;
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(@RequestParam("dataset") CsvDataset dataset,
                                  @RequestParam(value = "scope", defaultValue = "SELF") CsvScope scope,
                                  @RequestParam(value = "department", required = false) Long departmentId,
                                  @RequestParam("from") @DateTimeFormat(iso = DATE) LocalDate from,
                                  @RequestParam("to") @DateTimeFormat(iso = DATE) LocalDate to,
                                  Locale locale) {

        if (from.isAfter(to) || DAYS.between(from, to) > MAX_EXPORT_DAYS) {
            throw new IllegalArgumentException("invalid export period");
        }
        final Person signedInUser = personService.getSignedInUser();
        final List<Person> persons = scopeResolver.resolvePersons(signedInUser, scope, departmentId);
        final byte[] csv = csvImportExportService.export(dataset, persons, new DateRange(from, to), locale);
        return csvResponse(csv, "%s_%s_%s.csv".formatted(dataset.getFileName(), from, to));
    }

    @GetMapping("/template")
    ResponseEntity<byte[]> template(@RequestParam("dataset") CsvDataset dataset) {
        return csvResponse(csvImportExportService.template(dataset), "%s_vorlage.csv".formatted(dataset.getFileName()));
    }

    @PostMapping("/import")
    String preview(@RequestParam("dataset") CsvDataset dataset, @RequestParam("file") MultipartFile file, Model model) {
        final Person signedInUser = requireImportPermission();
        fillModel(model, signedInUser);
        model.addAttribute("importDataset", dataset);

        final String content;
        try {
            content = new String(file.getBytes(), UTF_8);
        } catch (IOException e) {
            model.addAttribute("importError", new CsvFormatException("fork.csv.error.unreadable", e.getMessage()));
            return VIEW;
        }

        try {
            model.addAttribute("importResult", csvImportExportService.preview(dataset, content, signedInUser));
            model.addAttribute("importContent", content);
        } catch (CsvFormatException e) {
            model.addAttribute("importError", e);
        }
        return VIEW;
    }

    @PostMapping("/import/execute")
    String execute(@RequestParam("dataset") CsvDataset dataset, @RequestParam("content") String content,
                   @RequestParam(value = "skipErrors", defaultValue = "false") boolean skipErrors, Model model) {
        final Person signedInUser = requireImportPermission();
        fillModel(model, signedInUser);
        model.addAttribute("importDataset", dataset);

        try {
            model.addAttribute("importResult", csvImportExportService.execute(dataset, content, signedInUser, skipErrors));
        } catch (CsvImportAbortedException e) {
            model.addAttribute("importResult", e.getResult());
            model.addAttribute("importContent", content);
            model.addAttribute("importAborted", true);
        } catch (CsvFormatException e) {
            model.addAttribute("importError", e);
        }
        return VIEW;
    }

    private Person requireImportPermission() {
        final Person signedInUser = personService.getSignedInUser();
        if (!scopeResolver.isAllowedToImport(signedInUser)) {
            throw new AccessDeniedException("User '%s' is not allowed to import csv files".formatted(signedInUser.getId()));
        }
        return signedInUser;
    }

    private void fillModel(Model model, Person signedInUser) {
        final List<CsvScope> scopes = new ArrayList<>();
        scopes.add(CsvScope.SELF);
        final var departments = scopeResolver.getExportableDepartments(signedInUser);
        if (!departments.isEmpty()) {
            scopes.add(CsvScope.DEPARTMENT);
        }
        if (scopeResolver.isAllowedToExportCompany(signedInUser)) {
            scopes.add(CsvScope.COMPANY);
        }

        final Year year = Year.now(clock);
        model.addAttribute("datasets", CsvDataset.values());
        model.addAttribute("scopes", scopes);
        model.addAttribute("departments", departments);
        model.addAttribute("canImport", scopeResolver.isAllowedToImport(signedInUser));
        model.addAttribute("exportFrom", year.atDay(1));
        model.addAttribute("exportTo", year.atMonth(12).atEndOfMonth());
    }

    private static ResponseEntity<byte[]> csvResponse(byte[] csv, String fileName) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CSV);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }
}
