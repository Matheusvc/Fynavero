import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";
import type {
  AnnualReport,
  AppNotification,
  Budget,
  Goal,
  GoalInput,
  NotificationPreferences,
  SearchResult,
  TagCount,
  CatalogItem,
  Category,
  CompareReport,
  Dashboard,
  ImportBatch,
  IncomeSource,
  Installments,
  Investments,
  Kind,
  Me,
  PaymentMethod,
  Providers,
  Recurrence,
  Scope,
  Tx,
  TxInput,
  TxUpdate,
} from "./types";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: async () => {
      try {
        return await api<Me>("/api/auth/me");
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 60_000,
  });
}

export const useProviders = () =>
  useQuery({ queryKey: ["providers"], queryFn: () => api<Providers>("/api/auth/providers"), staleTime: Infinity });

export const useCatalog = () =>
  useQuery({ queryKey: ["catalog"], queryFn: () => api<CatalogItem[]>("/api/catalog"), staleTime: Infinity });

export const useCategories = () =>
  useQuery({ queryKey: ["categories"], queryFn: () => api<Category[]>("/api/categories"), staleTime: 60_000 });

export type TxFilters = { q?: string; tag?: string; categoryId?: number; kind?: Kind; paymentMethod?: PaymentMethod };

export function useTransactions(month: string, f: TxFilters = {}) {
  const p = new URLSearchParams({ month });
  if (f.q) p.set("q", f.q);
  if (f.tag) p.set("tag", f.tag);
  if (f.categoryId) p.set("categoryId", String(f.categoryId));
  if (f.kind) p.set("kind", f.kind);
  if (f.paymentMethod) p.set("paymentMethod", f.paymentMethod);
  return useQuery({
    queryKey: ["transactions", month, f],
    queryFn: () => api<Tx[]>(`/api/transactions?${p}`),
    placeholderData: keepPreviousData,
  });
}

export const useDashboard = (month: string) =>
  useQuery({
    queryKey: ["dashboard", month],
    queryFn: () => api<Dashboard>(`/api/dashboard?month=${month}`),
    placeholderData: keepPreviousData,
  });

export const useInstallments = (month: string) =>
  useQuery({
    queryKey: ["installments", month],
    queryFn: () => api<Installments>(`/api/installments?month=${month}`),
    placeholderData: keepPreviousData,
  });

export const useInvestments = (month: string) =>
  useQuery({
    queryKey: ["investments", month],
    queryFn: () => api<Investments>(`/api/investments?month=${month}`),
    placeholderData: keepPreviousData,
  });

export const useAnnualReport = (year: number) =>
  useQuery({
    queryKey: ["reports", "annual", year],
    queryFn: () => api<AnnualReport>(`/api/reports/annual?year=${year}`),
    placeholderData: keepPreviousData,
  });

export const useCompareReport = (month: string) =>
  useQuery({
    queryKey: ["reports", "compare", month],
    queryFn: () => api<CompareReport>(`/api/reports/compare?month=${month}`),
    placeholderData: keepPreviousData,
  });

export const useImports = () => useQuery({ queryKey: ["imports"], queryFn: () => api<ImportBatch[]>("/api/imports") });

export const useIncomeSources = () =>
  useQuery({ queryKey: ["income-sources"], queryFn: () => api<IncomeSource[]>("/api/income-sources") });

export const useRecurrences = () =>
  useQuery({ queryKey: ["recurrences"], queryFn: () => api<Recurrence[]>("/api/recurrences") });

/** Busca no ano todo (ou no período): texto livre e/ou etiqueta. Só roda com algo para buscar. */
export function useSearch(f: { q?: string; tag?: string; from?: string; to?: string; kind?: Kind }) {
  const p = new URLSearchParams();
  if (f.q) p.set("q", f.q);
  if (f.tag) p.set("tag", f.tag);
  if (f.from) p.set("from", f.from);
  if (f.to) p.set("to", f.to);
  if (f.kind) p.set("kind", f.kind);
  return useQuery({
    queryKey: ["transactions", "search", f],
    queryFn: () => api<SearchResult>(`/api/transactions/search?${p}`),
    enabled: !!(f.q?.trim() || f.tag),
    placeholderData: keepPreviousData,
  });
}

export const useTags = () => useQuery({ queryKey: ["transactions", "tags"], queryFn: () => api<TagCount[]>("/api/transactions/tags") });

export const useBudgets = (month: string) =>
  useQuery({
    queryKey: ["budgets", month],
    queryFn: () => api<Budget[]>(`/api/budgets?month=${month}`),
    placeholderData: keepPreviousData,
  });

export const useGoals = () => useQuery({ queryKey: ["goals"], queryFn: () => api<Goal[]>("/api/goals") });

export function useGoalMutations() {
  const qc = useQueryClient();
  const done = () => {
    invalidateFinance(qc);
    void qc.invalidateQueries({ queryKey: ["categories"] });
  };
  return {
    create: useMutation({ mutationFn: (b: GoalInput) => api<Goal>("/api/goals", { method: "POST", body: b }), onSuccess: done }),
    update: useMutation({
      mutationFn: ({ id, body }: { id: number; body: GoalInput }) => api<Goal>(`/api/goals/${id}`, { method: "PUT", body }),
      onSuccess: done,
    }),
    remove: useMutation({ mutationFn: (id: number) => api<void>(`/api/goals/${id}`, { method: "DELETE" }), onSuccess: done }),
  };
}

/** Avisos do sino: atualizados a cada 5 minutos e depois de qualquer alteração financeira. */
export const useNotifications = (enabled = true) =>
  useQuery({
    queryKey: ["notifications"],
    queryFn: () => api<AppNotification[]>("/api/notifications"),
    refetchInterval: 5 * 60_000,
    enabled,
  });

export const useNotificationPreferences = () =>
  useQuery({ queryKey: ["notification-preferences"], queryFn: () => api<NotificationPreferences>("/api/notifications/preferences") });

/** Tudo que depende de lançamentos é atualizado na hora depois de qualquer alteração. */
export function invalidateFinance(qc: QueryClient) {
  for (const key of ["transactions", "dashboard", "installments", "investments", "reports", "imports", "recurrences", "income-sources", "budgets", "goals", "notifications"]) {
    void qc.invalidateQueries({ queryKey: [key] });
  }
}

export function useTxMutations() {
  const qc = useQueryClient();
  const done = () => invalidateFinance(qc);
  return {
    create: useMutation({ mutationFn: (b: TxInput) => api<Tx[]>("/api/transactions", { method: "POST", body: b }), onSuccess: done }),
    update: useMutation({
      mutationFn: ({ id, scope, body }: { id: number; scope: Scope; body: TxUpdate }) =>
        api<Tx>(`/api/transactions/${id}?scope=${scope}`, { method: "PUT", body }),
      onSuccess: done,
    }),
    remove: useMutation({
      mutationFn: ({ id, scope }: { id: number; scope: Scope }) =>
        api<{ ids: number[] }>(`/api/transactions/${id}?scope=${scope}`, { method: "DELETE" }),
      onSuccess: done,
    }),
    restore: useMutation({
      mutationFn: (ids: number[]) => api<void>("/api/transactions/restore", { method: "POST", body: { ids } }),
      onSuccess: done,
    }),
    duplicate: useMutation({
      mutationFn: (id: number) => api<Tx>(`/api/transactions/${id}/duplicate`, { method: "POST" }),
      onSuccess: done,
    }),
    markPaid: useMutation({
      mutationFn: (id: number) => api<Tx>(`/api/transactions/${id}/mark-paid`, { method: "POST" }),
      onSuccess: done,
    }),
    markUnpaid: useMutation({
      mutationFn: (id: number) => api<Tx>(`/api/transactions/${id}/mark-unpaid`, { method: "POST" }),
      onSuccess: done,
    }),
    removePlan: useMutation({
      mutationFn: (planId: number) => api<{ ids: number[] }>(`/api/installment-plans/${planId}`, { method: "DELETE" }),
      onSuccess: done,
    }),
  };
}

export function useSetMe() {
  const qc = useQueryClient();
  return (me: Me | null) => qc.setQueryData(["me"], me);
}
