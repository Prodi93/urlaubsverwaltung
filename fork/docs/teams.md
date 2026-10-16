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

1. **Teams SSO (empfohlen, auf BYOD-Geräten nötig):** Teams stellt dem Tab ein Entra-Token für die Urlaubsverwaltung aus,
   ohne Fenster und ohne App-Wechsel. Der Server prüft das Token (Signatur, Tenant, Zielgruppe) und meldet die Person an,
   die auch beim normalen Login erkannt würde: über das Subject, sonst über die E-Mail-Adresse. Personen, die die
   Urlaubsverwaltung noch nicht kennt, müssen sich einmal im Browser anmelden.
2. **Rückfall:** Ohne SSO-Einrichtung zeigt der Tab einen Button **Anmelden**, der die normale Anmeldung in einem
   Teams-Popup öffnet. Auf Geräten mit Intune-App-Schutzrichtlinien (BYOD) ist dieses Popup gesperrt („Der Wechsel von hier
   nach dort ist nicht möglich“). Dort hilft nur SSO.

### Teams SSO einrichten (einmalig)

In Entra ID → *App-Registrierungen* → die App-Registrierung der Urlaubsverwaltung (die mit der Client-ID aus
`spring.security.oauth2.client.registration.default.client-id`). Die genauen Werte zeigt *Einstellungen → Microsoft Teams*.

1. *Eine API verfügbar machen* → *URI der Anwendungs-ID*: `api://<host>/<client-id>`, z. B. `api://urlaub.firma.li/aaaa…`.
2. *Bereich hinzufügen*: `access_as_user`, zustimmen dürfen *Administratoren und Benutzer*, Status *aktiviert*.
3. *Clientanwendung hinzufügen* mit dem Bereich `access_as_user`:
   - `1fec8e78-bce4-4aaf-ab1b-5451cc387264` (Teams Desktop und Mobile)
   - `5e3ce6c0-2b1f-4285-8d4b-75ee78787346` (Teams Web)
4. Optional: *API-Berechtigungen* → *Berechtigung hinzufügen* → Tab *Von meiner Organisation verwendete APIs* → nach der
   App-Registrierung suchen → `access_as_user` → *Administratorzustimmung erteilen*. Nicht nötig, weil die Teams-Clients
   aus Schritt 3 ohne Zustimmung zugreifen dürfen. (*Meine APIs* zeigt nur App-Registrierungen, deren Besitzer man ist.)
5. Das App-Paket neu herunterladen (es enthält jetzt `webApplicationInfo`) und im Teams Admin Center bei der bestehenden
   App per *Datei hochladen* aktualisieren. Teams übernimmt die neue App-Version nach einigen Minuten, ggf. Teams neu starten.

## Update

Die App-Version im Paket steigt mit jedem Fork-Release (`6.17.0-li.4` wird zu `6.17.4`). Neu hochladen muss man das Paket
nur, wenn sich Tabs oder Texte der App ändern sollen: im Teams Admin Center die App öffnen und *Datei hochladen* wählen.

## Einschränkungen

- Teams im **Browser mit Safari** blockiert Cookies in eingebetteten Seiten. Dort funktioniert der Tab nicht, die Teams
  Desktop- und Mobile-App sind nicht betroffen.
