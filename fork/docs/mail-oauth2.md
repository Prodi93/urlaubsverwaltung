# E-Mail-Versand über OAuth2 (Microsoft 365 / Exchange Online)

Microsoft schaltet die Basic-Authentifizierung (Benutzername + Passwort) für SMTP ab. Der Fork kann Mails per
SMTP mit **XOAUTH2** versenden. Das Zugriffstoken wird per *Client-Credentials* direkt bei Entra ID geholt,
gecacht und kurz vor Ablauf erneuert. Ein Benutzer muss sich dafür nicht interaktiv anmelden.

## 1. Einrichtung in Entra ID und Exchange Online

1. **App registrieren** (Entra Admin Center → App-Registrierungen → Neue Registrierung), z. B. „Urlaubsverwaltung Mail“.
   - Notieren: *Verzeichnis-ID (Mandant)* und *Anwendungs-ID (Client)*.
   - Unter *Zertifikate & Geheimnisse* einen **geheimen Clientschlüssel** erstellen. Den Wert notieren und das Ablaufdatum im Kalender eintragen.
2. **API-Berechtigung** hinzufügen: *APIs, die meine Organisation verwendet* → **Office 365 Exchange Online** →
   *Anwendungsberechtigungen* → **`SMTP.SendAsApp`**, danach die **Administratorzustimmung erteilen**.
3. **Service Principal in Exchange registrieren und Postfachrechte vergeben** (PowerShell, Modul `ExchangeOnlineManagement`):

   ```powershell
   Connect-ExchangeOnline
   # AppId = Anwendungs-ID, ObjectId = Objekt-ID der *Unternehmensanwendung* (nicht der App-Registrierung!)
   New-ServicePrincipal -AppId <APP-ID> -ObjectId <OBJECT-ID> -DisplayName "Urlaubsverwaltung Mail"
   Add-MailboxPermission -Identity "urlaub@firma.li" -User <OBJECT-ID> -AccessRights FullAccess
   # SMTP AUTH muss für das Postfach erlaubt sein
   Set-CASMailbox -Identity "urlaub@firma.li" -SmtpClientAuthenticationDisabled $false
   ```

   Wird mit einer anderen Absenderadresse als dem Postfach versendet, braucht es zusätzlich
   `Add-RecipientPermission -Identity <absender> -Trustee <OBJECT-ID> -AccessRights SendAs`.

## 2. Konfiguration (`/opt/urlaubsverwaltung/application.yaml`)

```yaml
spring:
  mail:
    host: smtp.office365.com
    port: 587
    username: urlaub@firma.li          # Postfach, über das gesendet wird
    # password wird NICHT benötigt
    properties:
      mail.smtp.auth.login.disable: true
      mail.smtp.auth.plain.disable: true

uv:
  mail:
    from: urlaub@firma.li              # muss das Postfach sein (oder SendAs-Recht)
    replyTo: no-reply@firma.li
    application-url: https://urlaub.firma.li
  fork:
    mail:
      oauth2:
        enabled: true
        token-uri: https://login.microsoftonline.com/<TENANT-ID>/oauth2/v2.0/token
        client-id: <APP-ID>
        client-secret: <CLIENT-SECRET>
        # scope: https://outlook.office365.com/.default   (Standard)
        # refresh-skew: 5m                                  (Token wird 5 Minuten vor Ablauf erneuert)
```

Der Fork setzt automatisch `mail.smtp.auth=true`, `mail.smtp.auth.mechanisms=XOAUTH2` und STARTTLS (required),
sofern diese Werte nicht explizit gesetzt sind.

Ist `uv.fork.mail.oauth2.enabled` nicht `true`, arbeitet die Urlaubsverwaltung wie bisher mit `spring.mail.username`
und `spring.mail.password`.

## 3. Prüfen

- `systemctl restart urlaubsverwaltung && journalctl -u urlaubsverwaltung -f`
- Health-Check: `curl -s localhost:8080/actuator/health`. Ist das Mail-Health-Detail freigegeben, muss `mail` auf `UP` stehen.
  Der Health-Check baut eine SMTP-Verbindung mit dem Token auf.
- Typische Fehler:
  - `535 5.7.3 Authentication unsuccessful`: Service Principal fehlt, die Postfachberechtigung fehlt oder SMTP AUTH ist für das Postfach deaktiviert.
    Nach Änderungen in Exchange kann es bis zu 30 Minuten dauern, bis sie greifen.
  - `invalid_client` / `AADSTS7000215`: Client-Secret falsch oder abgelaufen.
  - `AADSTS1002012` / `invalid_scope`: Scope muss `https://outlook.office365.com/.default` sein.

## Google Workspace

Grundsätzlich funktioniert derselbe Mechanismus (`token-uri: https://oauth2.googleapis.com/token`, Host `smtp.gmail.com`).
Google erlaubt Client-Credentials für Gmail-SMTP allerdings nur über Service-Accounts mit Domain-Wide-Delegation (JWT).
Das ist **nicht** implementiert.
