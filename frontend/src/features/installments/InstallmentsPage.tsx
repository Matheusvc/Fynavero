import { useState } from "react";
import { CalendarClock, CircleCheck, Plus, Trash2 } from "lucide-react";
import { Money } from "@/components/Money";
import { Bars } from "@/components/charts/Bars";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/feedback";
import { PageHeader } from "@/components/ui/page-header";
import { Progress } from "@/components/ui/progress";
import { errorMessage } from "@/lib/api";
import { CategoryIcon } from "@/lib/icons";
import { useMonth } from "@/lib/month";
import { useCategories, useInstallments } from "@/lib/queries";
import { Modal } from "@/components/ui/dialog";
import { useUndoable } from "@/features/transactions/quick-add/useUndoable";
import type { Plan } from "@/lib/types";
import { monthName } from "@/lib/format";

export default function InstallmentsPage() {
  const { month } = useMonth();
  const data = useInstallments(month);
  const cats = useCategories();
  const { openNew } = useQuickAdd();
  const catById = new Map((cats.data ?? []).map((c) => [c.id, c]));
  const undoable = useUndoable();
  const [removing, setRemoving] = useState<Plan | null>(null);
  const planName = (p: Plan) => p.description || catById.get(p.categoryId)?.name || "Parcelamento";

  const action = (
    <Button onClick={() => openNew({ kind: "DESPESA", installments: true })}>
      <Plus /> Nova compra parcelada
    </Button>
  );

  if (data.isLoading) {
    return (
      <div>
        <PageHeader title="Parcelas e Dívidas" />
        <div className="space-y-3">
          <Skeleton className="h-64" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </div>
      </div>
    );
  }
  if (data.isError || !data.data) return <ErrorState message={errorMessage(data.error)} onRetry={() => data.refetch()} />;

  const { plans, timeline, monthTotal } = data.data;
  const active = plans.filter((p) => !p.finished);
  const finished = plans.filter((p) => p.finished);
  const remaining = active.reduce((s, p) => s + p.remainingCents, 0);

  return (
    <div className="space-y-4">
      <PageHeader title="Parcelas e Dívidas" description="Compras parceladas, empréstimos e financiamentos" actions={action} />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Card as="div">
          <p className="text-sm text-muted-foreground">Parcelas em {monthName(month)}</p>
          <Money cents={monthTotal} className="block text-2xl font-bold" />
        </Card>
        <Card as="div">
          <p className="text-sm text-muted-foreground">Ainda falta pagar</p>
          <Money cents={remaining} className="block text-2xl font-bold" />
          <p className="text-xs text-muted-foreground">
            {active.length} {active.length === 1 ? "parcelamento ativo" : "parcelamentos ativos"}
          </p>
        </Card>
      </div>

      <Card aria-labelledby="linha-tempo">
        <CardTitle id="linha-tempo">Total comprometido nos próximos 12 meses</CardTitle>
        <Bars
          title="Parcelas e contas fixas por mês nos próximos 12 meses"
          data={timeline}
          series={[
            { key: "installments", label: "Parcelas", color: "series-invest", stack: "a" },
            { key: "fixedBills", label: "Contas fixas", color: "series-expense", stack: "a" },
          ]}
        />
      </Card>

      {plans.length === 0 ? (
        <Card>
          <EmptyState icon={<CalendarClock className="size-7" />} title="Nenhuma parcela por aqui" actions={action}>
            Ao lançar uma compra parcelada, as próximas parcelas aparecem aqui e nos meses seguintes como Previsto.
          </EmptyState>
        </Card>
      ) : (
        <section aria-labelledby="ativos" className="space-y-3">
          <h2 id="ativos" className="text-lg font-semibold">Em andamento</h2>
          {active.length === 0 && <p className="text-sm text-muted-foreground">Tudo quitado. Parabéns!</p>}
          <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
            {active.map((p) => {
              const c = catById.get(p.categoryId);
              const current = Math.max(1, Math.min(p.count, p.current));
              return (
                <li key={p.id}>
                  <Card as="article" className="space-y-3">
                    <div className="flex items-start gap-3">
                      <CategoryIcon icon={c?.icon ?? "credit-card"} color={c?.color} />
                      <div className="min-w-0 flex-1">
                        <h3 className="truncate font-semibold">{p.description || c?.name}</h3>
                        <p className="text-xs text-muted-foreground">{c?.name}</p>
                      </div>
                      <div className="text-right">
                        <Money cents={p.installmentCents} className="block font-bold" />
                        <span className="text-xs text-muted-foreground">por mês</span>
                      </div>
                    </div>
                    <Progress value={p.paid} max={p.count} label={`${p.paid} de ${p.count} parcelas pagas`} />
                    <dl className="grid grid-cols-3 gap-2 text-sm">
                      <div>
                        <dt className="text-xs text-muted-foreground">Parcela atual</dt>
                        <dd className="money font-semibold">{current}/{p.count}</dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted-foreground">Falta</dt>
                        <dd><Money cents={p.remainingCents} className="font-semibold" /></dd>
                      </div>
                      <div>
                        <dt className="text-xs text-muted-foreground">Última parcela</dt>
                        <dd className="font-semibold">{p.lastMonth}</dd>
                      </div>
                    </dl>
                    <div className="flex justify-end border-t pt-2">
                      <Button variant="ghost" size="sm" onClick={() => setRemoving(p)} aria-label={`Excluir ${planName(p)}`}>
                        <Trash2 /> Excluir parcelamento
                      </Button>
                    </div>
                  </Card>
                </li>
              );
            })}
          </ul>
          {finished.length > 0 && (
            <>
              <h2 className="pt-2 text-lg font-semibold">Quitados</h2>
              <ul className="divide-y rounded-xl border bg-card">
                {finished.map((p) => (
                  <li key={p.id} className="flex items-center gap-3 px-4 py-3 text-sm">
                    <CircleCheck className="size-5 text-positive" aria-hidden="true" />
                    <span className="flex-1">{p.description || catById.get(p.categoryId)?.name}</span>
                    <Badge tone="positive">Quitado</Badge>
                    <Money cents={p.totalCents} />
                    <Button variant="ghost" size="icon" onClick={() => setRemoving(p)} aria-label={`Excluir ${planName(p)}`}>
                      <Trash2 />
                    </Button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}

      <Modal
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title="Excluir este parcelamento?"
        footer={
          <>
            <Button variant="outline" onClick={() => setRemoving(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (removing) undoable.deletePlan(removing.id, planName(removing));
                setRemoving(null);
              }}
            >
              <Trash2 /> Excluir todas as parcelas
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">
          “{removing ? planName(removing) : ""}” sai de Parcelas e Dívidas, junto com as {removing?.count} parcelas em Lançamentos.
          Se foi sem querer, use “Desfazer” logo em seguida.
        </p>
      </Modal>
    </div>
  );
}
