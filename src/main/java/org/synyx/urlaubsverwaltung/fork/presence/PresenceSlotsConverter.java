package org.synyx.urlaubsverwaltung.fork.presence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores {@link PresenceSlots} as text, compatible with the former single {@link PresenceType} per day.
 */
@Converter
class PresenceSlotsConverter implements AttributeConverter<PresenceSlots, String> {

    @Override
    public String convertToDatabaseColumn(PresenceSlots slots) {
        return slots == null ? null : slots.asString();
    }

    @Override
    public PresenceSlots convertToEntityAttribute(String value) {
        return value == null || value.isBlank() ? null : PresenceSlots.parse(value);
    }
}
