package br.com.fynavero.common;

public final class ApiLimits {
    private ApiLimits() {}

    /** Maior valor aceito em um lançamento: R$ 1 bilhão, em centavos. */
    public static final long MAX_CENTS = 100_000_000_000L;
}
