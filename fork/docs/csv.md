# CSV-Import und -Export

Menü **Import / Export** (`/web/import-export`).

## Export

| Wer | Darf exportieren |
|---|---|
| alle | eigene Daten |
| Abteilungsleitung / Freigabeverantwortliche | zusätzlich die Mitglieder der verwalteten Abteilungen |
| Chef / Office | jede Abteilung und das ganze Unternehmen |

Format: Trennzeichen `;`, UTF-8 mit BOM (öffnet in Excel direkt mit Umlauten), Datumswerte `yyyy-MM-dd`,
Dezimalpunkt. Eine exportierte Datei lässt sich unverändert wieder importieren, dabei ändert sich nichts.

## Import (nur Office)

1. Daten wählen, CSV-Datei hochladen, dann **Prüfen**. Es erscheint eine Vorschau pro Zeile (neu / geändert / gelöscht /
   unverändert / Fehler), gespeichert wird noch nichts.
2. **Importieren**: Alles oder nichts. Bei Fehlern wird nichts gespeichert, außer *Zeilen mit Fehlern überspringen* ist
   gewählt.

Beim Import werden auch `,` als Trennzeichen und Datumswerte im Format `dd.MM.yyyy` erkannt, ebenso `,` als
Dezimaltrennzeichen. Personen werden über `username` erkannt, ohne Benutzernamen über `email`. Vorlagen mit allen
Spalten gibt es auf der Seite.

| Daten | Spalten (Pflicht **fett**) | Verhalten |
|---|---|---|
| Abwesenheiten | `username`/`email`, **`vacation_type`** (Bezeichnung, z. B. „Erholungsurlaub“), **`start_date`**, **`end_date`**, `day_length` (FULL/MORNING/NOON), `status` (ALLOWED, WAITING, CANCELLED, REJECTED), `hours`, `reason` | Wird **ohne Genehmigungsablauf** gespeichert: keine E-Mails, keine Kalendersynchronisation, Kommentar „CSV-Import“. Bestehende Abwesenheiten bleiben unverändert, Überschneidungen sind Fehler. |
| Urlaubskonten | **`year`**, `valid_from`, `valid_to`, **`annual_vacation_days`**, **`actual_vacation_days`**, **`remaining_vacation_days`**, `remaining_vacation_days_not_expiring`, `do_remaining_vacation_days_expire` (leer = globale Einstellung), `expiry_date`, `comment` | Legt das Konto an oder aktualisiert es. |
| Anwesenheiten | **`date`**, **`type`** (OFFICE, HOME_OFFICE, MOBILE_WORK) | Ein leerer `type` entfernt die geplante Anwesenheit. Exportiert werden nur geplante Tage, nicht die aus dem Wochenmuster. |
| Personen & Arbeitszeiten | `username`, `email`, `first_name`, `last_name`, `departments`, **`valid_from`**, **`working_days`** (MON,TUE,… oder MO,DI,…), `federal_state` (leer = Standard), `part_time_percentage` | Unbekannte Personen werden angelegt; das Office erhält dafür wie gewohnt eine E-Mail. Beim ersten Login per OIDC wird die Person über die E-Mail zugeordnet. `departments` wird nur exportiert. |
