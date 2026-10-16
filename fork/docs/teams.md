# Microsoft Teams

Die Urlaubsverwaltung lässt sich als App in Microsoft Teams einbinden:

- **App-Leiste:** Teamkalender, Meine Anwesenheit, Übersicht und Meine Aufgaben als persönliche Tabs
- **Kanäle und Chats:** als Registerkarte mit frei wählbarer Seite, z. B. dem Teamkalender

Angezeigt wird die normale Oberfläche, es gibt keine zweite Datenhaltung.

## Einrichtung

1. In `/opt/urlaubsverwaltung/application.yaml` die Einbindung aktivieren und den Dienst neu starten:

   ```yaml
   uv:
     fork:
       teams:
         enabled: true
   ```

   Damit gilt:
   - Teams darf die Seiten einbetten (`Content-Security-Policy: frame-ancestors` statt `X-Frame-Options`).
   - Das Session-Cookie wird mit `SameSite=None; Secure` gesetzt. Dafür ist HTTPS nötig.
2. In der Urlaubsverwaltung als Office: **Einstellungen → Microsoft Teams → App-Paket herunterladen**. Die Adressen im
   Paket stammen aus `uv.mail.application-url`.
3. [Teams Admin Center](https://admin.teams.microsoft.com) → *Teams-Apps → Apps verwalten → Neue App hochladen* → ZIP auswählen.
4. Optional: *Teams-Apps → Einrichtungsrichtlinien → Global → Angeheftete Apps* → App hinzufügen, damit sie bei allen
   links in der App-Leiste erscheint.
5. Kanal-Tab: im Kanal **+** → App auswählen → Seite wählen.

In Entra ID ist nichts einzurichten. Die Anmeldung läuft über die bestehende OIDC-App-Registrierung.

## Anmeldung

Microsoft lässt seine Anmeldeseite nicht in einem eingebetteten Frame anzeigen. Ist man in Teams noch nicht angemeldet,
zeigt der Tab deshalb einen Button **Anmelden**. Er öffnet die normale Anmeldung in einem Teams-Popup, danach lädt der Tab
die Seite. Läuft die Sitzung in Teams ab, landet man automatisch wieder auf diesem Button.

## Update

Die App-Version im Paket steigt mit jedem Fork-Release (`6.17.0-li.4` wird zu `6.17.4`). Neu hochladen muss man das Paket
nur, wenn sich Tabs oder Texte der App ändern sollen: im Teams Admin Center die App öffnen und *Datei hochladen* wählen.

## Einschränkungen

- Teams im **Browser mit Safari** blockiert Cookies in eingebetteten Seiten. Dort funktioniert der Tab nicht, die Teams
  Desktop- und Mobile-App sind nicht betroffen.
- Einmal-Anmeldung ohne Klick (Teams SSO) ist nicht umgesetzt.
