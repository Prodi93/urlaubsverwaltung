package org.synyx.urlaubsverwaltung.fork.parttime;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Part time free days of a person in a year.
 *
 * @param year     the year
 * @param computed computed quota from the working time and the part time percentage
 * @param override manual correction of the quota, replaces the computed value if present
 * @param comment  comment of the manual correction
 * @param used     days of allowed applications (incl. cancellation requested)
 * @param waiting  days of waiting and temporary allowed applications
 */
public record PartTimeFreeDaysSummary(
    int year,
    BigDecimal computed,
    Optional<BigDecimal> override,
    Optional<String> comment,
    BigDecimal used,
    BigDecimal waiting
) {

    /**
     * @return the quota of the year, i.e. the override if present, else the computed quota
     */
    public BigDecimal quota() {
        return override.orElse(computed);
    }

    /**
     * @return days left, i.e. quota minus used and waiting days
     */
    public BigDecimal left() {
        return quota().subtract(used).subtract(waiting);
    }

    public boolean isOverridden() {
        return override.isPresent();
    }

    /**
     * @return {@code true} if there is anything to show for this year
     */
    public boolean hasQuota() {
        return quota().signum() != 0 || used.signum() != 0 || waiting.signum() != 0;
    }
}
