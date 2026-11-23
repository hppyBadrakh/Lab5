package com.ecommerce.order.export;

public class AuditTaxCalculationFilter implements Filter<ExportStages.Normalized, ExportStages.Taxed> {

    private static final double TAX_RATE = 0.10;

    @Override
    public ExportStages.Taxed process(ExportStages.Normalized order) {
        return new ExportStages.Taxed(order, order.amountUsd().multiply(TAX_RATE));
    }
}
