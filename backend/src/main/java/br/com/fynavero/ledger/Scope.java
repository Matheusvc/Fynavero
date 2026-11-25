package br.com.fynavero.ledger;

/**
 * "Só este", "Este e os próximos" ou "Todas" ao editar ou excluir lançamentos recorrentes e parcelados.
 * ALL só existe na exclusão de parcelas: apaga a compra parcelada inteira (todas as parcelas).
 */
public enum Scope { ONE, FOLLOWING, ALL }
