# Urlaubsverwaltung – LI-Fork

Fork von [urlaubsverwaltung/urlaubsverwaltung](https://github.com/urlaubsverwaltung/urlaubsverwaltung) mit Erweiterungen
für den Einsatz in Liechtenstein. Basis ist das jeweils letzte stabile Upstream-Release (aktuell **6.17.0**).

## Erweiterungen

| Funktion | Beschreibung | Doku |
|---|---|---|
| Feiertage Liechtenstein | Regionen „Liechtenstein“ (gesetzlich) und „Liechtenstein (inkl. Bankfeiertage)“ mit Berchtoldstag, Fasnachtsdienstag und Karfreitag. Heiligabend/Silvester weiterhin über *Einstellungen → Feiertage*. | – |
| Mail über OAuth2 | SMTP mit XOAUTH2 (Microsoft 365 / Exchange Online) statt Benutzername/Passwort | [docs/mail-oauth2.md](docs/mail-oauth2.md) |
| PWA | Installierbar auf Smartphone/Desktop, Offline-Seite, Shortcuts | – |
| Teilzeit ohne festen freien Tag | Pensum pro Person (historisiert), automatisch berechnetes Kontingent an „Teilzeit-Freitagen“, eigene Abwesenheitsart | – |
| Anwesenheit Büro/Homeoffice | *in Arbeit* | – |
| CSV-Import/-Export | *in Arbeit* | – |

## Branches, Versionen und Releases

- `main` spiegelt Upstream, dort wird nicht entwickelt.
- `li/main` ist der Produktionszweig: das Upstream-Release-Tag plus die Fork-Commits.
- Version `<upstream>-li.<n>`, z. B. `6.17.0-li.1`. Ein Release entsteht durch das Tag `li-<version>`:

  ```bash
  git tag li-6.17.0-li.1 && git push origin li-6.17.0-li.1
  ```

  Der Workflow `.github/workflows/fork-release.yml` baut und testet das Projekt und hängt
  `urlaubsverwaltung-<version>.jar` an das GitHub-Release.
- Die Upstream-Workflows (`build.yml`, `release.yml`, …) im Fork unter *Actions* deaktivieren, weil ihnen die Secrets fehlen.
- Neues Upstream-Release übernehmen:

  ```bash
  git fetch upstream --tags
  git rebase --onto urlaubsverwaltung-<neu> urlaubsverwaltung-6.17.0 li/main
  ```

### Konventionen für Fork-Code

- Java unter `org.synyx.urlaubsverwaltung.fork.*`, Templates unter `templates/fork/`, JS unter `js/fork/`.
- Texte in `messages-fork*.properties`.
- Liquibase in `dbchangelogs/fork/` (Tabellen mit Präfix `fork_`). Der einzige Eintrag in `changelogmaster.xml` ist das
  `include` am Ende und muss bei jedem Rebase das letzte bleiben.
- Eingriffe in Upstream-Code klein halten, möglichst über Erweiterungspunkte (Interfaces, Interceptors).

## Umstieg der Produktivinstanz (Proxmox LXC, bisher 6.14.0 + `uv-holidays-li.sh`)

Bisher war die Region **„Malta“** ausgewählt. `custom/uv-holidays-li.sh` hat deren Kalender in der JAR durch die
liechtensteinischen Feiertage inkl. Bankfeiertage ersetzt. Der Fork migriert `MALTA` beim ersten Start automatisch nach
`LIECHTENSTEIN_BANK_HOLIDAYS` (Liquibase `fork-0001`). Das gilt für die Firmeneinstellung und für Personen-Overrides. Die
Feiertage sind identisch, die Urlaubssalden ändern sich also nicht.

1. **Testlauf:** LXC klonen (oder DB-Dump in einen Test-LXC einspielen) und dort zuerst umstellen.
2. Vorher Salden sichern, z. B. *Abwesenheiten → Statistik → CSV-Export* für das laufende Jahr.
3. Update-Skript ersetzen: `fork/proxmox/ct-urlaubsverwaltung.sh` lädt Releases aus `Prodi93/urlaubsverwaltung`,
   sichert DB und JAR nach `/opt/urlaubsverwaltung-backup/` und prüft nach dem Start `/actuator/health`.
4. Update ausführen. Das LI-Skript wird nicht mehr aufgerufen.
5. Prüfen:
   - *Einstellungen → Feiertage*: Region „Liechtenstein – Liechtenstein (inkl. Bankfeiertage)“
   - Kalender: 2.1., Fasnachtsdienstag und Karfreitag sind Feiertage.
   - Salden mit dem Export aus Schritt 2 vergleichen.
6. Aufräumen: `/opt/urlaubsverwaltung/custom/` und `/opt/urlaubsverwaltung/urlaubsverwaltung.bak` löschen.

**Rollback:** Service stoppen, den DB-Dump aus `/opt/urlaubsverwaltung-backup/db-*.sql.gz` einspielen und die alte JAR
aus demselben Verzeichnis zurückkopieren. Sie enthält bereits den gepatchten Malta-Kalender.

## Lokal entwickeln

```bash
export JAVA_HOME=~/.jdks/jdk-25.0.3      # Java 25 (siehe .tool-versions)
./mvnw clean verify                     # Build inkl. Unit- und Integrationstests (Docker nötig)
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=demodata"
```
