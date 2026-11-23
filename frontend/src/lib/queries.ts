import { keepPreviousData, useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";
import type {
  AnnualReport,
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

export type TxFilters = { q?: string; categoryId?: number; kind?: Kind; paymentMethod?: PaymentMethod };

export function useTransactions(month: string, f: TxFilters = {}) {
  const p = new URLSearchParams({ month });
  if (f.q) p.set("q", f.q);
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

/** Tudo que depende de lançamentos é atualizado na hora depois de qualquer alteração. */
export function invalidateFinance(qc: QueryClient) {
  for (const key of ["transactions", "dashboard", "installments", "investments", "reports", "imports", "recurrences", "income-sources"]) {
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
  };
}

export function useSetMe() {
  const qc = useQueryClient();
  return (me: Me | null) => qc.setQueryData(["me"], me);
}
