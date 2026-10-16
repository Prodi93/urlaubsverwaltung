# CLAUDE.md – Urlaubsverwaltung, LI-Fork

Fork von [urlaubsverwaltung/urlaubsverwaltung](https://github.com/urlaubsverwaltung/urlaubsverwaltung) für ein Unternehmen
in Liechtenstein (Repo `Prodi93/urlaubsverwaltung`). Details zu Funktionen und Betrieb stehen in [fork/README.md](fork/README.md), dazu [fork/docs/mail-oauth2.md](fork/docs/mail-oauth2.md) und
[fork/docs/csv.md](fork/docs/csv.md).

## Arbeitsweise

- Mit dem User **auf Deutsch** kommunizieren. UI-Texte auf Deutsch mit „du“ (wie Upstream), Englisch als zweite Sprache.
- Größere Vorhaben vorher kurz abstimmen (Fragen stellen, wenn eine Entscheidung beim User liegt), dann selbstständig umsetzen.
- **Ein Commit pro Feature bzw. Fix** auf `li/main`, Commit-Messages auf Englisch im Upstream-Stil (`fork: …`). Pushen nach
  `origin` ist erlaubt.
- Vor einem Release den **vollständigen Build** grün haben (siehe unten). Danach Tag setzen und den GitHub-Actions-Lauf abwarten.
- Upstream-Code nur minimal anfassen. Fork-Code gehört in eigene Pakete und Dateien (siehe Konventionen), damit spätere
  Upstream-Rebases einfach bleiben.
- Seiten immer auch **mit Erfolgs- und Fehlermeldungen (Flash-Attribute) rendern** lassen, siehe *Stolpersteine*.

## Branches, Versionen, Releases

- `main` spiegelt Upstream (Remote `upstream`), dort wird nicht entwickelt. `li/main` ist der Produktionszweig, basiert auf
  dem Upstream-Tag `urlaubsverwaltung-6.17.0`.
- Version `<upstream>-li.<n>`. Release per Tag `li-<version>` (z. B. `git tag -a li-6.17.0-li.4 -m "…" && git push origin li-6.17.0-li.4`).
  `.github/workflows/fork-release.yml` baut mit `-Dgroups="!ui"` und hängt `urlaubsverwaltung-<version>.jar` plus `.sha256`
  ans GitHub-Release. Lauf beobachten: `gh run list -R Prodi93/urlaubsverwaltung`, `gh run watch <id> -R Prodi93/urlaubsverwaltung`.
- Upstream-Release übernehmen: `git fetch upstream --tags && git rebase --onto urlaubsverwaltung-<neu> urlaubsverwaltung-6.17.0 li/main`.
  Danach muss das `include` der Fork-Changelogs weiterhin der **letzte** Eintrag in `changelogmaster.xml` sein.

## Produktion (Proxmox-LXC)

- JAR `/opt/urlaubsverwaltung/urlaubsverwaltung`, Konfiguration `/opt/urlaubsverwaltung/application.yaml`, systemd-Dienst
  `urlaubsverwaltung`, Postgres 18 lokal, Login per OIDC, Java 25.
- **Update im Container als root: `uv-fork-update`** (installiert das neueste Fork-Release), oder `uv-fork-update li-6.17.0-li.3`
  für ein bestimmtes Release. Das Skript liegt im Repo unter [fork/proxmox/uv-fork-update.sh](fork/proxmox/uv-fork-update.sh).
  Es sichert DB und JAR nach `/opt/urlaubsverwaltung-backup/` und wartet auf `/actuator/health`.
- **Nie** den `update`-Befehl der community-scripts verwenden, er installiert die Upstream-Version.
- Vor jedem Update macht der User einen Proxmox-Snapshot.
- Historie: Bis 6.14.0 lief Upstream mit der Region „Malta“, deren Kalender `custom/uv-holidays-li.sh` per Hack durch die
  LI-Feiertage ersetzte. Liquibase `fork-0001` migriert `MALTA` → `LIECHTENSTEIN_BANK_HOLIDAYS`.

## Lokal bauen und testen

Kein System-Java: JDK 25 liegt in `~/.jdks/jdk-25.0.3`. Docker (Desktop, WSL-Integration) ist für Testcontainers nötig.

```bash
cd /home/prodi/Urlaubsverwaltung/urlaubsverwaltung
export JAVA_HOME=~/.jdks/jdk-25.0.3 PATH=~/.jdks/jdk-25.0.3/bin:$PATH

./mvnw -B -ntp clean verify -Djacoco.skip=true          # alles: npm lint/test/build, Unit, IT, UI (~25-30 min)
npm run build                                            # Frontend bauen (nötig, bevor ITs mit -Dskip.npm laufen)
./mvnw -B -ntp -o test -Dskip.npm -Dskip.installnodenpm -Dtest='PresenceServiceTest'               # einzelne Unit-Tests
./mvnw -B -ntp verify -Dskip.npm -Dskip.installnodenpm -Dtest=NoSuch -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='PresenceViewIT,PresenceCalendarUIIT' -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true  # einzelne ITs/UI-Tests
npx vitest run src/main/javascript/js/fork && npx eslint src/main/javascript && npx prettier --check src/main/resources/templates/fork
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=demodata"   # App lokal (docker compose startet automatisch)
```

- `-o` (offline) nur, wenn alle Plugins schon im `~/.m2` liegen. `verify` braucht online meist das jar-Plugin.
- UI-Tests (`*UIIT`, Playwright, Tag `ui`) laufen lokal mit, in CI ausgeschlossen. Schlägt ein UI-Test fehl, zum Vergleich auf
  Upstream gegenprüfen (`git worktree add <dir> urlaubsverwaltung-6.17.0`).

## Konventionen für Fork-Code

- Java: `org.synyx.urlaubsverwaltung.fork.<feature>` (publicholiday, mail.oauth2, pwa, parttime, presence, csv, application).
  Ausnahme: `application/application/ForkApplicationForLeaveCreator` (braucht package-private Upstream-Klassen).
- Templates `templates/fork/**`, JS `src/main/javascript/js/fork/**` (Tests in `__tests__`), Bundle `bundles/fork-presence.js`.
- Texte in `messages-fork{,_en,_el}.properties` (Basename in `application.yaml` **und** `src/test/resources/application.yaml`).
- Liquibase in `dbchangelogs/fork/` (`fork-000N-….xml`, Autor `prodi`), Tabellen mit Präfix `fork_`. Entities erben von
  `AbstractTenantAwareEntity`, Sequenz-`incrementBy` = `allocationSize` (10).
- Properties unter `uv.fork.*`.
- Erweiterungspunkte statt Upstream-Umbau: `AdditionalPublicHolidaysProvider`, `AdditionalApplicationValidator`,
  `DataProviderInterface`-Interceptoren (z. B. `PartTimeFreeDaysDataProvider`, `PresenceWeekDataProvider`,
  `VacationDaysLeftDataProvider`) mit eigenen `WebMvcConfigurer`n, eigene `SecurityFilterChain` (`PwaSecurityConfiguration`).
- Bewusste Upstream-Eingriffe (bei Rebases prüfen): `FederalState`, `FederalStateDTO`, `PublicHolidaysServiceImpl`,
  `PublicHolidayConfiguration`, `VacationCategory` (+ `VacationTypeCategoryDTO`, `VacationTypeServiceImpl`),
  `ApplicationForLeaveFormValidator`, `WebManifestController`, `FrameDataProvider` (Navigation), `_layout.html`, `common.js`,
  `js/person/overview.js`, einige Templates (`application_form.html`, `section-calendar.html`, `section-vacation-days.html`,
  `workingtime_form.html`), `changelogmaster.xml`, die zugehörigen Upstream-Tests.

## Funktionen (Kurzüberblick)

- **Feiertage LI**: `LIECHTENSTEIN`, `LIECHTENSTEIN_BANK_HOLIDAYS` (+ Berchtoldstag, Fasnachtsdienstag, Karfreitag).
- **Mail OAuth2** (M365, SMTP XOAUTH2): `uv.fork.mail.oauth2.*`. Ersetzt den Boot-MailSender nur, wenn aktiviert.
- **PWA**: `/sw.js`, `/offline`, Manifest mit Shortcuts. Kein Navigation-Preload (brach den OIDC-Login).
- **Teilzeit ohne festen freien Tag**: `fork_part_time`, `fork_part_time_quota`, Kategorie `PARTTIME_FREEDAY`, Seite
  `/web/person/{id}/parttime`.
- **Teamkalender/Anwesenheit**: `/web/presence` ersetzt die Abwesenheitsübersicht (`/web/absences` leitet um). Halbtage als
  `PresenceSlots` (in der DB `OFFICE|HOME_OFFICE`). Monatsplanung `/web/presence/persons/{id}` beantragt Abwesenheiten über
  `ForkApplicationForLeaveCreator`. Box „Diese Woche“ und Menü im Kalender der Übersichtsseite (`js/fork/presence-calendar.js`).
  Farben: Büro blau (sky), Homeoffice grün (emerald), Mobil lila (violet).
- **CSV**: `/web/import-export`. Export je nach Rolle, Import nur Office (Vorschau, alles oder nichts).
- **Antragsformular**: zeigt den verbleibenden Erholungsurlaub und die Teilzeit-Freitage.

## Stolpersteine

- `fragments/feedback-box::feedback-box-success(...)` erwartet ein **Fragment**, keinen Text. Für Texte
  `~{fork/feedback::success(text=#{…})}` verwenden (sonst Serverfehler nach dem Speichern).
- Zahlen in Messages mit Parametern: Jahre als `{0,number,#}`, sonst erscheint „2.026“.
- Fragmente in `templates/fork/presence/fragments.html` immer absolut referenzieren (`~{fork/presence/fragments::half(...)}`).
  Hilfsfragmente einer Seite gehören hinter `</main>`, sonst werden sie mitgerendert.
- Tailwind-Klassen müssen im Template oder JS stehen, damit sie generiert werden. Dynamische Farben (Abwesenheitsarten)
  laufen über CSS-Variablen (`var(--absence-color-<COLOR>)`).
- ArchUnit: Seiten-Controller implementieren `HasLaunchpad` und `HasPersonSearch`. Entities siehe oben.
- ITs teilen sich einen Postgres-Container pro JVM. Nicht-transaktionale ITs müssen ihre Daten aufräumen, sonst scheitern
  Upstream-Repository-ITs.
- MockMvc: denselben Parameter nicht zweimal setzen, sonst gewinnt bei `@RequestParam Map` der erste Wert.
- eslint (unicorn) verbietet `null` in JS, `undefined` verwenden. Templates und JS mit prettier formatieren.
