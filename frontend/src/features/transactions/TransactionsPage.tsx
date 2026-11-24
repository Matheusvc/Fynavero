import { useDeferredValue, useMemo, useState } from "react";
import { Link } from "react-router";
import { FileSpreadsheet, Plus, Repeat, Search, SlidersHorizontal, X } from "lucide-react";
import { Money } from "@/components/Money";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { PageHeader } from "@/components/ui/page-header";
import { Segmented } from "@/components/ui/segmented";
import { TagList } from "@/components/TagInput";
import { errorMessage } from "@/lib/api";
import { formatDayHeader, formatPct, monthLabel } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { KIND_LABEL, PAYMENT_METHODS, paymentLabel } from "@/lib/labels";
import { useMonth } from "@/lib/month";
import { useCategories, useMe, useSearch, useTags, useTransactions, type TxFilters } from "@/lib/queries";
import type { Category, Kind, PaymentMethod, Tx } from "@/lib/types";
import { cn } from "@/lib/utils";

function TagChip({ label, selected, onClick }: { label: string; selected: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={cn(
        "inline-flex min-h-9 shrink-0 items-center rounded-full border px-3 text-sm transition-colors",
        selected ? "border-primary bg-accent font-medium text-accent-foreground" : "bg-card text-muted-foreground hover:bg-muted",
      )}
    >
      #{label}
    </button>
  );
}

function signed(t: Tx) {
  return t.kind === "RECEITA" ? t.amountCents : -t.amountCents;
}

function TxRow({ tx, cat, onOpen, onTag }: { tx: Tx; cat?: Category; onOpen: () => void; onTag: (t: string) => void }) {
  const title = tx.description || cat?.name || "Lançamento";
  const pm = paymentLabel(tx.paymentMethod);
  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        className="flex min-h-16 w-full items-center gap-3 px-3 py-2.5 text-left transition-colors hover:bg-muted sm:px-4"
      >
        <CategoryIcon icon={cat?.icon ?? "tag"} color={cat?.color} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium">{title}</p>
          <p className="flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
            {tx.description && cat && <span>{cat.name}</span>}
            {pm && <span>· {pm}</span>}
            {tx.kind === "INVESTIMENTO" && <Badge tone="brand">Investido</Badge>}
            {tx.status === "PREVISTO" && <Badge tone="forecast">Previsto</Badge>}
            {tx.installmentNumber && (
              <Badge tone={tx.installmentNumber === tx.installmentTotal ? "brand" : "neutral"}>
                {tx.installmentNumber}/{tx.installmentTotal}
                {tx.installmentNumber === tx.installmentTotal && " · última"}
              </Badge>
            )}
            {tx.recurrenceId && (
              <span className="inline-flex items-center gap-0.5">
                <Repeat className="size-3" aria-hidden="true" /> todo mês
              </span>
            )}
            {tx.origin === "IMPORTADO" && <Badge>Importado</Badge>}
          </p>
          {tx.tags.length > 0 && <TagList tags={tx.tags} onSelect={onTag} className="mt-1" />}
        </div>
        {tx.kind === "RECEITA" ? (
          <Money cents={tx.amountCents} tone="positive" signed className="font-semibold" />
        ) : tx.kind === "INVESTIMENTO" ? (
          <Money cents={tx.amountCents} className="font-semibold" />
        ) : (
          <Money cents={-tx.amountCents} className="font-semibold" />
        )}
      </button>
    </li>
  );
}

export default function TransactionsPage() {
  const { month } = useMonth();
  const { openNew, openEdit } = useQuickAdd();
  const cats = useCategories();
  const me = useMe();
  const [q, setQ] = useState("");
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [kind, setKind] = useState<Kind | undefined>();
  const [payment, setPayment] = useState<PaymentMethod | undefined>();
  const [showFilters, setShowFilters] = useState(false);
  const [tag, setTag] = useState<string | undefined>();
  const [scope, setScope] = useState<"month" | "year">("month");
  const tags = useTags();
  const dq = useDeferredValue(q.trim());
  const year = month.slice(0, 4);
  const yearMode = scope === "year";

  const filters: TxFilters = { q: dq || undefined, tag, categoryId, kind, paymentMethod: payment };
  const monthTxs = useTransactions(month, filters);
  const search = useSearch(yearMode ? { q: dq || undefined, tag, kind, from: `${year}-01-01`, to: `${year}-12-31` } : {});
  const waitingQuery = yearMode && !dq && !tag;
  const txs = yearMode ? search : monthTxs;
  const list = useMemo(() => {
    if (!yearMode) return monthTxs.data ?? [];
    return (search.data?.items ?? []).filter(
      (t) => (!categoryId || t.categoryId === categoryId) && (!payment || t.paymentMethod === payment),
    );
  }, [yearMode, monthTxs.data, search.data, categoryId, payment]);
  const catById = useMemo(() => new Map((cats.data ?? []).map((c) => [c.id, c])), [cats.data]);
  const active = !!(dq || tag || categoryId || kind || payment);

  const days = useMemo(() => {
    const map = new Map<string, Tx[]>();
    for (const t of list) map.set(t.date, [...(map.get(t.date) ?? []), t]);
    return [...map.entries()];
  }, [list]);

  const totals = useMemo(() => {
    let rec = 0, des = 0, inv = 0;
    for (const t of list) {
      if (t.kind === "RECEITA") rec += t.amountCents;
      else if (t.kind === "DESPESA") des += t.amountCents;
      else inv += t.amountCents;
    }
    return { rec, des, inv };
  }, [list]);

  function clear() {
    setQ("");
    setCategoryId(undefined);
    setKind(undefined);
    setPayment(undefined);
    setTag(undefined);
  }

  return (
    <div>
      <PageHeader
        title="Lançamentos"
        description={yearMode ? `Busca no ano de ${year}` : monthLabel(month)}
        actions={
          <Button onClick={() => openNew()}>
            <Plus /> Lançar
          </Button>
        }
      />

      <div className="mb-4 space-y-3">
        <div className="flex gap-2">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Buscar: farmácia, mercado, #viagem…"
              aria-label="Buscar lançamentos"
              className="pl-9"
            />
          </div>
          <Button
            variant="outline"
            onClick={() => setShowFilters((v) => !v)}
            aria-expanded={showFilters}
            aria-controls="tx-filters"
            className={cn(active && "border-primary")}
          >
            <SlidersHorizontal /> <span className="hidden sm:inline">Filtros</span>
          </Button>
        </div>
        <Segmented
          label="Onde buscar"
          value={scope}
          onChange={setScope}
          options={[
            { value: "month", label: monthLabel(month) },
            { value: "year", label: `Ano todo (${year})` },
          ]}
          className="sm:max-w-md"
        />
        {(tag || (tags.data?.length ?? 0) > 0) && (
          <div className="flex gap-1.5 overflow-x-auto pb-1" role="group" aria-label="Filtrar por etiqueta">
            {tag && !tags.data?.some((t) => t.tag === tag) && <TagChip label={tag} selected onClick={() => setTag(undefined)} />}
            {(tags.data ?? []).slice(0, 12).map((t) => (
              <TagChip key={t.tag} label={t.tag} selected={tag === t.tag} onClick={() => setTag(tag === t.tag ? undefined : t.tag)} />
            ))}
          </div>
        )}
        {showFilters && (
          <div id="tx-filters" className="grid grid-cols-1 gap-2 sm:grid-cols-3">
            <Select aria-label="Filtrar por categoria" value={categoryId ?? ""} onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">Todas as categorias</option>
              {(cats.data ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({KIND_LABEL[c.kind]})
                </option>
              ))}
            </Select>
            <Select aria-label="Filtrar por tipo" value={kind ?? ""} onChange={(e) => setKind((e.target.value || undefined) as Kind | undefined)}>
              <option value="">Todos os tipos</option>
              <option value="DESPESA">Despesas</option>
              <option value="RECEITA">Receitas</option>
              <option value="INVESTIMENTO">Investimentos</option>
            </Select>
            <Select
              aria-label="Filtrar por forma de pagamento"
              value={payment ?? ""}
              onChange={(e) => setPayment((e.target.value || undefined) as PaymentMethod | undefined)}
            >
              <option value="">Todas as formas de pagamento</option>
              {PAYMENT_METHODS.map((p) => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </Select>
          </div>
        )}
        {active && (
          <Button variant="link" onClick={clear}>
            <X /> Limpar filtros
          </Button>
        )}
      </div>

      {list.length > 0 && (
        <dl className="mb-4 grid grid-cols-3 gap-2 text-center text-sm">
          <div className="rounded-lg bg-card p-2 shadow-sm">
            <dt className="text-xs text-muted-foreground">Receitas</dt>
            <dd><Money cents={totals.rec} className="font-semibold" /></dd>
          </div>
          <div className="rounded-lg bg-card p-2 shadow-sm">
            <dt className="text-xs text-muted-foreground">Despesas</dt>
            <dd><Money cents={totals.des} className="font-semibold" /></dd>
          </div>
          <div className="rounded-lg bg-card p-2 shadow-sm">
            <dt className="text-xs text-muted-foreground">Investido</dt>
            <dd><Money cents={totals.inv} className="font-semibold" /></dd>
          </div>
        </dl>
      )}
      {yearMode && search.data?.truncated && (
        <p className="-mt-2 mb-4 text-xs text-muted-foreground">
          Mostrando os 500 lançamentos mais recentes. Refine a busca para ver o restante.
        </p>
      )}
      {me.data?.contribution.enabled && list.length > 0 && !active && !yearMode && (
        <p className="-mt-2 mb-4 text-xs text-muted-foreground">
          No Início, as despesas também incluem a contribuição automática de {formatPct(me.data.contribution.bps)} sobre as receitas.
        </p>
      )}

      {waitingQuery ? (
        <Card>
          <EmptyState icon={<Search className="size-7" />} title={`Buscar em ${year}`}>
            Digite o que procura (ex.: farmácia) ou escolha uma etiqueta para ver os lançamentos do ano todo.
          </EmptyState>
        </Card>
      ) : txs.isLoading ? (
        <div className="space-y-2" aria-busy="true" aria-label="Carregando">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      ) : txs.isError ? (
        <ErrorState message={errorMessage(txs.error)} onRetry={() => txs.refetch()} />
      ) : days.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Search className="size-7" />}
            title={active ? "Nada encontrado" : `Nenhum lançamento em ${monthLabel(month)}`}
            actions={
              active ? (
                <Button variant="outline" onClick={clear}>Limpar filtros</Button>
              ) : (
                <>
                  <Button onClick={() => openNew()}><Plus /> Lançar</Button>
                  <Button variant="outline" asChild>
                    <Link to="/importar"><FileSpreadsheet /> Importar planilha</Link>
                  </Button>
                </>
              )
            }
          >
            {active ? "Tente outra busca ou limpe os filtros." : "Seus gastos e rendas deste mês aparecem aqui."}
          </EmptyState>
        </Card>
      ) : (
        <div className="space-y-4">
          {days.map(([date, list]) => {
            const total = list.reduce((s, t) => s + signed(t), 0);
            return (
              <section key={date} aria-labelledby={`d-${date}`} className="overflow-hidden rounded-xl border bg-card shadow-sm">
                <div className="flex items-center justify-between border-b bg-muted px-3 py-2 text-sm sm:px-4">
                  <h2 id={`d-${date}`} className="font-semibold">{formatDayHeader(date)}</h2>
                  <span className="text-muted-foreground">
                    Total do dia <Money cents={total} signed className="font-semibold text-foreground" />
                  </span>
                </div>
                <ul className="divide-y">
                  {list.map((t) => (
                    <TxRow key={t.id} tx={t} cat={catById.get(t.categoryId)} onOpen={() => openEdit(t)} onTag={setTag} />
                  ))}
                </ul>
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
