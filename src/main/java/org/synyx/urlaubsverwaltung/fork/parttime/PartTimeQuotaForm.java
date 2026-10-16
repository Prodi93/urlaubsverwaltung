package org.synyx.urlaubsverwaltung.fork.parttime;

import java.math.BigDecimal;

public class PartTimeQuotaForm {

    private BigDecimal overrideDays;

    private String comment;

    public BigDecimal getOverrideDays() {
        return overrideDays;
    }

    public void setOverrideDays(BigDecimal overrideDays) {
        this.overrideDays = overrideDays;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
