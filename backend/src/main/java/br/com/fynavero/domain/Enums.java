package br.com.fynavero.domain;

public final class Enums {
    private Enums() {}

    public enum Kind { DESPESA, RECEITA, INVESTIMENTO }

    public enum Status { REALIZADO, PREVISTO }

    public enum Origin { MANUAL, IMPORTADO }

    public enum Objective { ORGANIZAR, DIVIDAS, INVESTIR, OBJETIVO }

    public enum PaymentMethod { PIX, DINHEIRO, DEBITO, CREDITO, VA_VR, VT, BOLETO, TRANSFERENCIA }

    /** "Só este" ou "Este e os próximos" em lançamentos recorrentes e parcelados. */
    public enum Scope { ONE, FOLLOWING }
}
