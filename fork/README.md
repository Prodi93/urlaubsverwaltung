# Urlaubsverwaltung – LI-Fork

Fork von [urlaubsverwaltung/urlaubsverwaltung](https://github.com/urlaubsverwaltung/urlaubsverwaltung) mit Erweiterungen
für den Einsatz in Liechtenstein. Basis ist das jeweils letzte stabile Upstream-Release (aktuell **6.17.0**).

## Erweiterungen

| Funktion | Beschreibung | Doku |
|---|---|---|
| Feiertage Liechtenstein | Regionen „Liechtenstein“ (gesetzlich) und „Liechtenstein (inkl. Bankfeiertage)“ mit Berchtoldstag, Fasnachtsdienstag und Karfreitag. Heiligabend/Silvester weiterhin über *Einstellungen → Feiertage*. | – |
| Mail über OAuth2 | SMTP mit XOAUTH2 (Microsoft 365 / Exchange Online) statt Benutzername/Passwort | [docs/mail-oauth2.md](docs/mail-oauth2.md) |
| PWA | Installierbar auf Smartphone/Desktop, Offline-Seite, Shortcuts | – |
| Microsoft Teams | Als App in Teams (App-Leiste und Kanal-Tabs), Anmeldung über ein Teams-Popup | [docs/teams.md](docs/teams.md) |
| Antragsformular | Zeigt den verbleibenden Erholungsurlaub und die verbleibenden Teilzeit-Freitage | – |
| Teilzeit ohne festen freien Tag | Pensum pro Person (historisiert), automatisch berechnetes Kontingent an „Teilzeit-Freitagen“, eigene Abwesenheitsart | – |
| Teamkalender (Anwesenheit + Abwesenheit) | Ersetzt die Abwesenheitsübersicht (`/web/absences` leitet um). Zeigt pro Tag und Halbtag: Büro (blau), Homeoffice (grün), Mobil (lila), Abwesenheiten in der Farbe der Abwesenheitsart (beantragt = umrandet; Details nur für Berechtigte, sonst „abwesend“), Feiertage. Zeile „Im Büro“ zählt halbe Tage mit. Planung im Monat (inkl. Abwesenheiten beantragen und Halbtage), Wochenmuster, Box „Diese Woche“ und Menü im Kalender der Übersichtsseite, Schnell-Eintrag *Heute*, REST-API `/api/persons/{id}/presences`. Abschaltbar mit `uv.fork.presence.replace-absence-overview=false` (nur die Umleitung). | – |
| CSV-Import/-Export | Export für sich, Abteilung oder Unternehmen: Abwesenheiten, Urlaubskonten, Anwesenheiten, Personen & Arbeitszeiten (inkl. Teilzeit). Import (Office) mit Vorschau, alles oder nichts. | [docs/csv.md](docs/csv.md) |

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

1. Proxmox-Snapshot des Containers anlegen.
2. Im Container als root:

   ```bash
   curl -fsSLO https://raw.githubusercontent.com/Prodi93/urlaubsverwaltung/li/main/fork/proxmox/uv-fork-update.sh
   bash uv-fork-update.sh
   ```

   Das Skript lädt das neueste Fork-Release und prüft die sha256-Summe. Danach stoppt es den Dienst, sichert DB und JAR
   nach `/opt/urlaubsverwaltung-backup/`, tauscht die JAR und verschiebt den alten Malta-Hack (`custom/`, `.bak`) ins
   Backup. Anschließend startet es den Dienst, wartet auf `/actuator/health` und zeigt die Feiertagsregion an. Zum
   Schluss installiert es sich als `uv-fork-update` für spätere Updates.
3. Prüfen:
   - *Einstellungen → Feiertage*: Region „Liechtenstein – Liechtenstein (inkl. Bankfeiertage)“
   - Kalender: 2.1., Fasnachtsdienstag und Karfreitag sind Feiertage.
   - Neue Menüpunkte „Anwesenheit“ und „Import / Export“.
4. **Updates künftig nur noch mit `uv-fork-update`**, nicht mehr mit dem `update`-Befehl der community-scripts.
   Der würde das Upstream-Release installieren.

**Rollback:** Proxmox-Snapshot zurückspielen. Ohne Snapshot: Dienst stoppen, den DB-Dump aus
`/opt/urlaubsverwaltung-backup/db-*.sql.gz` in eine leere Datenbank einspielen und die alte JAR aus demselben
Verzeichnis zurückkopieren. Sie enthält bereits den gepatchten Malta-Kalender.

## Lokal entwickeln

```bash
export JAVA_HOME=~/.jdks/jdk-25.0.3      # Java 25 (siehe .tool-versions)
./mvnw clean verify                     # Build inkl. Unit- und Integrationstests (Docker nötig)
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=demodata"
```
