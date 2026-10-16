package org.synyx.urlaubsverwaltung.fork.presence;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Texts of the team calendar, used in the templates as {@code ${@forkPresenceLabels.title(day)}}.
 */
@Component("forkPresenceLabels")
public class PresenceLabels {

    private final MessageSource messageSource;

    PresenceLabels(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String title(PresenceDay day) {
        final Locale locale = LocaleContextHolder.getLocale();
        if (!day.isSplit()) {
            return label(day.morning(), locale);
        }
        return message("fork.presence.morning", locale) + ": " + label(day.morning(), locale)
            + " / " + message("fork.presence.noon", locale) + ": " + label(day.noon(), locale);
    }

    public String label(PresenceDay.Half half) {
        return label(half, LocaleContextHolder.getLocale());
    }

    private String label(PresenceDay.Half half, Locale locale) {
        return switch (half.kind()) {
            case PRESENCE -> message("fork.presence.type." + half.type().name(), locale)
                + (half.explicit() ? "" : " (" + message("fork.presence.pattern.short", locale) + ")");
            case UNPLANNED -> message("fork.presence.unplanned", locale);
            case ABSENT -> half.absence().label() + (half.absence().waiting() ? " (" + message("fork.presence.waiting", locale) + ")" : "");
            case PUBLIC_HOLIDAY -> message("fork.presence.publicHoliday", locale);
            case NO_WORKDAY -> message("fork.presence.noWorkday", locale);
        };
    }

    private String message(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
    }
}
