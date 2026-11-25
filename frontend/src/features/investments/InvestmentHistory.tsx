import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Pencil, PiggyBank, Trash2 } from "lucide-react";
import { Money } from "@/components/Money";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState, Skeleton } from "@/components/ui/feedback";
import { Select } from "@/components/ui/field";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { api } from "@/lib/api";
import { formatDate, monthLabel, todayISO } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { useCategories } from "@/lib/queries";
import type { SearchResult, Tx } from "@/lib/types";

const PAGE = 20;

/** Período máximo da busca (5 anos): 4 para trás e 1 para a frente, para incluir aportes programados. */
function range() {
  const today = todayISO();
  const y = Number(today.slice(0, 4));
  return { from: `${y - 4}${today.slice(4)}`, to: `${y + 1}${today.slice(4)}` };
}

/** Todos os investimentos lançados, do mais novo para o mais antigo, com editar (valor, data, categoria) e excluir. */
export function InvestmentHistory() {
  const { openEdit, openNew, deleteWithUndo } = useQuickAdd();
  const cats = useCategories();
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [shown, setShown] = useState(PAGE);
  const { from, to } = range();
  const history = useQuery({
    queryKey: ["transactions", "investment-history", from, to],
    queryFn: () => api<SearchResult>(`/api/transactions/search?kind=INVESTIMENTO&from=${from}&to=${to}`),
  });
  const catById = useMemo(() => new Map((cats.data ?? []).map((c) => [c.id, c])), [cats.data]);
  const investCats = (cats.data ?? []).filter((c) => c.kind === "INVESTIMENTO");
  const items = (history.data?.items ?? []).filter((t) => !categoryId || t.categoryId === categoryId);

  const byMonth = useMemo(() => {
    const map = new Map<string, Tx[]>();
    for (const t of items.slice(0, shown)) map.set(t.date.slice(0, 7), [...(map.get(t.date.slice(0, 7)) ?? []), t]);
    return [...map.entries()];
  }, [items, shown]);

  return (
    <Card aria-labelledby="historico-investimentos">
      <CardTitle
        id="historico-investimentos"
        action={
          investCats.length > 1 && (
          <Select
            aria-label="Filtrar por categoria"
            value={categoryId ?? ""}
            onChange={(e) => {
              setCategoryId(e.target.value ? Number(e.target.value) : undefined);
              setShown(PAGE);
            }}
            className="min-h-10 w-auto text-sm"
          >
            <option value="">Todas as categorias</option>
            {investCats.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </Select>
          )
        }
      >
        Histórico de investimentos
      </CardTitle>

      {history.isLoading ? (
        <div className="space-y-2" aria-busy="true">
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<PiggyBank className="size-7" />}
          title="Nenhum investimento lançado"
          actions={<Button onClick={() => openNew({ kind: "INVESTIMENTO", categoryId })}>Investir</Button>}
        >
          Cada valor que você guardar aparece aqui, com a data, e pode ser corrigido depois.
        </EmptyState>
      ) : (
        <div className="space-y-4">
          {byMonth.map(([ym, list]) => (
            <section key={ym} aria-label={monthLabel(ym)}>
              <h3 className="mb-1 flex justify-between text-sm font-semibold text-muted-foreground">
                <span className="capitalize">{monthLabel(ym)}</span>
                <Money cents={list.reduce((s, t) => s + t.amountCents, 0)} />
              </h3>
              <ul className="divide-y rounded-lg border">
                {list.map((t) => {
                  const c = catById.get(t.categoryId);
                  return (
                    <li key={t.id} className="flex items-center gap-3 px-3 py-2">
                      <CategoryIcon icon={c?.icon ?? "piggy-bank"} color={c?.color} size="sm" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{t.description || c?.name || "Investimento"}</p>
                        <p className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                          {formatDate(t.date)}
                          {t.description && c && <span>· {c.name}</span>}
                          {t.status === "PREVISTO" && <Badge tone="forecast">Previsto</Badge>}
                          {t.recurrenceId && <span>· todo mês</span>}
                        </p>
                      </div>
                      <Money cents={t.amountCents} className="text-sm font-semibold" />
                      <Button variant="ghost" size="icon" aria-label={`Editar investimento de ${formatDate(t.date)}`} onClick={() => openEdit(t)}>
                        <Pencil />
                      </Button>
                      <Button variant="ghost" size="icon" aria-label={`Excluir investimento de ${formatDate(t.date)}`} onClick={() => void deleteWithUndo(t)}>
                        <Trash2 />
                      </Button>
                    </li>
                  );
                })}
              </ul>
            </section>
          ))}
          {items.length > shown && (
            <Button variant="outline" className="w-full" onClick={() => setShown((n) => n + PAGE)}>
              Ver mais ({items.length - shown})
            </Button>
          )}
        </div>
      )}
    </Card>
  );
}
