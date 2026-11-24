import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import {
  ArrowDownRight,
  ArrowUpRight,
  CalendarClock,
  CircleCheck,
  FileSpreadsheet,
  Lightbulb,
  Minus,
  Pencil,
  Plus,
  TriangleAlert,
  Wallet,
} from "lucide-react";
import { Money } from "@/components/Money";
import { Bars, Donut } from "@/components/charts";
import { useQuickAdd } from "@/components/QuickAdd";
import { Button } from "@/components/ui/button";
import { Badge, Card, CardTitle, EmptyState, ErrorState, Skeleton, Spinner } from "@/components/ui/misc";
import { Modal } from "@/components/ui/dialog";
import { errorMessage } from "@/lib/api";
import { changeBps, formatPct, formatShortDay, monthName, shiftMonth } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { useMonth } from "@/lib/month";
import { useCategories, useDashboard, useMe, useTransactions, useTxMutations } from "@/lib/queries";
import type { Slice, Totals } from "@/lib/types";

function Change({ current, previous, month }: { current: number; previous: number; month: string }) {
  const bps = changeBps(current, previous);
  const prevName = monthName(shiftMonth(month, -1));
  if (bps === null) return <p className="text-xs text-muted-foreground">Sem dados de {prevName}</p>;
  const I = bps > 0 ? ArrowUpRight : bps < 0 ? ArrowDownRight : Minus;
  const word = bps > 0 ? "a mais" : bps < 0 ? "a menos" : "igual";
  return (
    <p className="flex items-center gap-1 text-xs text-muted-foreground">
      <I className="size-3.5" aria-hidden="true" />
      <span>
        {bps === 0 ? `Igual a ${prevName}` : `${formatPct(Math.abs(bps))} ${word} que em ${prevName}`}
      </span>
    </p>
  );
}

function StatCard({ label, cents, prev, month, note }: { label: string; cents: number; prev: number; month: string; note?: ReactNode }) {
  return (
    <Card as="div" className="space-y-1">
      <p className="text-sm text-muted-foreground">{label}</p>
      <Money cents={cents} className="block text-2xl font-bold" />
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
      <Change current={cents} previous={prev} month={month} />
    </Card>
  );
}

/**
 * Lançamentos por trás de uma linha de "Para onde foi seu dinheiro".
 * Tocar num lançamento abre a edição completa (valor, categoria, parcelas…).
 */
function CategoryPanel({
  slice,
  slices,
  month,
  contribution,
  contributionCategoryId,
  onClose,
}: {
  slice: Slice;
  slices: Slice[];
  month: string;
  contribution: number;
  contributionCategoryId?: number | null;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const { openEdit } = useQuickAdd();
  const cats = useCategories();
  const txs = useTransactions(month, { kind: "DESPESA" });
  const catById = new Map((cats.data ?? []).map((c) => [c.id, c]));
  const isOthers = slice.categoryId == null;
  const topIds = new Set(slices.map((s) => s.categoryId).filter((id): id is number => id != null));
  const list = (txs.data ?? []).filter((t) => (isOthers ? !topIds.has(t.categoryId) : t.categoryId === slice.categoryId));
  const hasContribution = !isOthers && contribution > 0 && slice.categoryId === contributionCategoryId;
  const autoOpened = useRef(false);

  useEffect(() => {
    if (autoOpened.current || !txs.data || isOthers || hasContribution || list.length !== 1) return;
    autoOpened.current = true;
    onClose();
    openEdit(list[0]!);
  }, [txs.data, isOthers, hasContribution, list, onClose, openEdit]);

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={`${slice.name} · ${monthName(month)}`} description="Toque em um lançamento para editar.">
      {txs.isLoading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Carregando…</p>
      ) : (
        <ul className="divide-y">
          {hasContribution && (
            <li className="flex flex-wrap items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">Contribuição automática</p>
                <p className="text-xs text-muted-foreground">Calculada sobre as receitas do mês</p>
              </div>
              <Money cents={-contribution} className="font-semibold" />
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onClose();
                  navigate("/configuracoes#contribuicao");
                }}
              >
                Ajustar contribuição
              </Button>
            </li>
          )}
          {list.map((t) => {
            const c = catById.get(t.categoryId);
            return (
              <li key={t.id}>
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    openEdit(t);
                  }}
                  className="flex min-h-14 w-full items-center gap-3 py-2 text-left hover:bg-muted"
                >
                  <CategoryIcon icon={c?.icon ?? "tag"} color={c?.color} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{t.description || c?.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatShortDay(t.date)}
                      {isOthers && c && ` · ${c.name}`}
                      {t.installmentNumber && ` · parcela ${t.installmentNumber}/${t.installmentTotal}`}
                      {t.status === "PREVISTO" && " · Previsto"}
                    </p>
                  </div>
                  <Money cents={-t.amountCents} className="font-semibold" />
                  <Pencil className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                </button>
              </li>
            );
          })}
          {list.length === 0 && !hasContribution && (
            <li className="py-4 text-sm text-muted-foreground">Nenhum lançamento encontrado.</li>
          )}
        </ul>
      )}
    </Modal>
  );
}

function Hero({ t, month }: { t: Totals; month: string }) {
  const negative = t.saldo < 0;
  return (
    <section
      aria-labelledby="saldo"
      className="relative overflow-hidden rounded-xl bg-secondary p-5 text-secondary-foreground shadow-md sm:p-7"
    >
      <span className="absolute inset-y-0 left-0 w-1.5 bg-primary" aria-hidden="true" />
      <h2 id="saldo" className="flex items-center gap-2 text-sm font-medium opacity-80">
        <Wallet className="size-4" aria-hidden="true" /> Saldo para passar o mês · {monthName(month)}
      </h2>
      <p className="mt-2 text-hero font-bold">
        <Money cents={t.saldo} />
      </p>
      {negative && (
        <p className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-negative px-2 py-1 text-sm font-semibold text-negative-foreground">
          <TriangleAlert className="size-4" aria-hidden="true" /> Saldo negativo
        </p>
      )}
      <p className="mt-3 text-sm opacity-80">
        Sobra do mês <Money cents={t.sobra} className="font-semibold" /> − Investido{" "}
        <Money cents={t.investido} className="font-semibold" />
      </p>
    </section>
  );
}

export default function HomePage() {
  const { month } = useMonth();
  const me = useMe();
  const dash = useDashboard(month);
  const cats = useCategories();
  const { openNew } = useQuickAdd();
  const { markPaid } = useTxMutations();
  const navigate = useNavigate();
  const [panel, setPanel] = useState<Slice | null>(null);
  const closePanel = useCallback(() => setPanel(null), []);

  if (dash.isLoading) {
    return (
      <div className="space-y-4" aria-busy="true" aria-label="Carregando">
        <Skeleton className="h-44" />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
        <Skeleton className="h-72" />
      </div>
    );
  }
  if (dash.isError || !dash.data) return <ErrorState message={errorMessage(dash.error)} onRetry={() => dash.refetch()} />;

  const d = dash.data;
  const catById = new Map((cats.data ?? []).map((c) => [c.id, c]));
  const firstName = me.data?.name?.split(" ")[0];

  if (d.empty) {
    return (
      <Card>
        <EmptyState
          icon={<Wallet className="size-7" />}
          title={firstName ? `Olá, ${firstName}! Vamos começar?` : "Vamos começar?"}
          actions={
            <>
              <Button onClick={() => openNew()}>
                <Plus /> Lançar primeiro gasto
              </Button>
              <Button variant="outline" asChild>
                <Link to="/importar">
                  <FileSpreadsheet /> Importar planilha
                </Link>
              </Button>
            </>
          }
        >
          Lance seu primeiro gasto ou traga sua planilha. Em segundos você vê para onde vai seu dinheiro.
        </EmptyState>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="sr-only">Início</h1>
      <Hero t={d.current} month={month} />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard
          label="Receitas"
          cents={d.current.receitas}
          prev={d.previous.receitas}
          month={month}
          note={d.current.emprestimos > 0 ? <>inclui <Money cents={d.current.emprestimos} /> de empréstimo</> : undefined}
        />
        <StatCard label="Despesas" cents={d.current.despesas} prev={d.previous.despesas} month={month} />
        <StatCard label="Investido" cents={d.current.investido} prev={d.previous.investido} month={month} />
      </div>

      {d.insights.length > 0 && (
        <Card aria-labelledby="insights">
          <CardTitle id="insights">Para você</CardTitle>
          <ul className="space-y-2">
            {d.insights.map((t) => (
              <li key={t} className="flex gap-3 text-sm">
                <Lightbulb className="mt-0.5 size-4 shrink-0 text-primary-text" aria-hidden="true" />
                <span>{t}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card aria-labelledby="para-onde">
          <CardTitle id="para-onde">Para onde foi seu dinheiro</CardTitle>
          {d.breakdown.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">Nenhuma despesa neste mês.</p>
          ) : (
            <Donut
              slices={d.breakdown}
              total={d.current.despesas}
              title="Despesas por categoria"
              onEdit={(s) => (s.categoryId === -1 ? navigate("/configuracoes#contribuicao") : setPanel(s))}
            />
          )}
          {panel && (
            <CategoryPanel
              slice={panel}
              slices={d.breakdown}
              month={month}
              contribution={d.current.contribuicao}
              contributionCategoryId={me.data?.contribution.categoryId}
              onClose={closePanel}
            />
          )}
        </Card>

        <Card aria-labelledby="vencimentos">
          <CardTitle id="vencimentos">Próximos vencimentos</CardTitle>
          {d.upcoming.length === 0 ? (
            <div className="flex flex-col items-center gap-2 py-6 text-center text-sm text-muted-foreground">
              <CalendarClock className="size-6" aria-hidden="true" />
              Nada vence nos próximos 15 dias.
            </div>
          ) : (
            <ul className="divide-y">
              {d.upcoming.map((u) => {
                const c = catById.get(u.categoryId);
                return (
                  <li key={u.id} className="flex items-center gap-3 py-2.5">
                    <CategoryIcon icon={c?.icon ?? "tag"} color={c?.color} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{u.description || c?.name}</p>
                      <p className="text-xs text-muted-foreground">
                        Vence {formatShortDay(u.date)}
                        {u.installmentNumber && ` · parcela ${u.installmentNumber}/${u.installmentTotal}`}
                      </p>
                    </div>
                    <Money cents={u.amountCents} className="text-sm font-semibold" />
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Marcar ${u.description || c?.name} como pago`}
                      onClick={() =>
                        markPaid.mutate(u.id, {
                          onSuccess: () => toast.success("Marcado como pago"),
                          onError: (e) => toast.error(errorMessage(e)),
                        })
                      }
                    >
                      <CircleCheck />
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      </div>

      <Card aria-labelledby="seis-meses">
        <CardTitle id="seis-meses">Seus últimos 6 meses</CardTitle>
        <Bars
          title="Receitas, despesas e investido nos últimos 6 meses"
          data={d.lastMonths}
          series={[
            { key: "receitas", label: "Receitas", color: "series-income" },
            { key: "despesas", label: "Despesas", color: "series-expense" },
            { key: "investido", label: "Investido", color: "series-invest" },
          ]}
        />
      </Card>

      {d.current.contribuicao > 0 && (
        <p className="text-center text-xs text-muted-foreground">
          As despesas incluem <Money cents={d.current.contribuicao} /> de contribuição automática sobre as receitas.{" "}
          <Badge tone="neutral">Configurações › Contribuição</Badge>
        </p>
      )}
    </div>
  );
}
