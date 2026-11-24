import { useCallback, useState } from "react";
import { Link, useNavigate } from "react-router";
import { toast } from "sonner";
import { CalendarClock, CircleCheck, FileSpreadsheet, Lightbulb, Plus, Wallet } from "lucide-react";
import { Money } from "@/components/Money";
import { Bars } from "@/components/charts/Bars";
import { Donut } from "@/components/charts/Donut";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api";
import { formatShortDay } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { useMonth } from "@/lib/month";
import { useCategories, useDashboard, useMe, useTxMutations } from "@/lib/queries";
import type { Slice } from "@/lib/types";
import { Hero, StatCard } from "./SummaryCards";
import { CategoryPanel } from "./CategoryPanel";
import { BudgetCard } from "./BudgetCard";
import { GoalsCard } from "./GoalsCard";

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

      <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-2">
        <BudgetCard month={month} />
        <GoalsCard />
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
