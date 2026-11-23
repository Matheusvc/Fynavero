import type { Kind, Objective, PaymentMethod } from "./types";

export const PAYMENT_METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "PIX", label: "Pix" },
  { value: "DINHEIRO", label: "Dinheiro" },
  { value: "DEBITO", label: "Débito" },
  { value: "CREDITO", label: "Crédito" },
  { value: "VA_VR", label: "VA/VR" },
  { value: "VT", label: "VT" },
  { value: "BOLETO", label: "Boleto" },
  { value: "TRANSFERENCIA", label: "Transferência" },
];

export const paymentLabel = (p?: PaymentMethod | null) => PAYMENT_METHODS.find((x) => x.value === p)?.label;

export const OBJECTIVES: { value: Objective; label: string; hint: string; icon: string }[] = [
  { value: "ORGANIZAR", label: "Organizar meus gastos", hint: "Saber para onde vai cada real", icon: "list-checks" },
  { value: "DIVIDAS", label: "Sair das dívidas", hint: "Acompanhar parcelas e quitar o que deve", icon: "trending-down" },
  { value: "INVESTIR", label: "Começar a investir", hint: "Separar um valor todo mês", icon: "trending-up" },
  { value: "OBJETIVO", label: "Juntar para um objetivo", hint: "Viagem, casa, reserva…", icon: "target" },
];

export const KIND_LABEL: Record<Kind, string> = {
  DESPESA: "Despesa",
  RECEITA: "Receita",
  INVESTIMENTO: "Investimento",
};

export const KIND_PLURAL: Record<Kind, string> = {
  DESPESA: "Despesas",
  RECEITA: "Receitas",
  INVESTIMENTO: "Investimentos",
};

export const EXPENSE_GROUPS = ["Essenciais", "Estilo de vida", "Compromissos", "Família", "Contribuições", "Outros"];
