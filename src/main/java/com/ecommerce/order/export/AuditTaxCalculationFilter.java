package com.ecommerce.order.export;

import java.math.BigDecimal;

public class AuditTaxCalculationFilter implements Filter<ExportStages.Normalized, ExportStages.Taxed> {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");

    @Override
    public ExportStages.Taxed process(ExportStages.Normalized order) {
        return new ExportStages.Taxed(order, order.amountUsd().multiply(TAX_RATE));
    }
}
