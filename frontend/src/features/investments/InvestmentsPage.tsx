import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, PiggyBank, Plus, Target } from "lucide-react";
import { Money } from "@/components/Money";
import { Bars } from "@/components/charts/Bars";
import { CurrencyInput } from "@/components/CurrencyInput";
import { IconPicker } from "@/components/IconPicker";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/field";
import { Card, CardTitle } from "@/components/ui/card";
import { EmptyState, ErrorState, Skeleton, Spinner } from "@/components/ui/feedback";
import { PageHeader } from "@/components/ui/page-header";
import { Progress } from "@/components/ui/progress";
import { api, errorMessage } from "@/lib/api";
import { centsToInput, formatBRL, monthName, parseMoneyInput } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { useMonth } from "@/lib/month";
import { invalidateFinance, useInvestments } from "@/lib/queries";
import type { InvestmentCategory } from "@/lib/types";

type Draft = { id?: number; name: string; goal: string; icon: string };

export default function InvestmentsPage() {
  const { month } = useMonth();
  const data = useInvestments(month);
  const qc = useQueryClient();
  const { openNew } = useQuickAdd();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    if (!draft || !draft.name.trim()) return;
    setSaving(true);
    const goalCents = draft.goal ? parseMoneyInput(draft.goal) : null;
    try {
      const body = { name: draft.name.trim(), kind: "INVESTIMENTO", icon: draft.icon, goalCents: goalCents ?? 0 };
      if (draft.id) await api(`/api/categories/${draft.id}`, { method: "PUT", body });
      else await api("/api/categories", { method: "POST", body });
      await qc.invalidateQueries({ queryKey: ["categories"] });
      invalidateFinance(qc);
      toast.success(draft.id ? "Categoria atualizada" : "Categoria criada");
      setDraft(null);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  const edit = (c: InvestmentCategory) =>
    setDraft({ id: c.categoryId, name: c.name, icon: c.icon, goal: c.goalCents ? centsToInput(c.goalCents) : "" });

  return (
    <div className="space-y-4">
      <PageHeader
        title="Investimentos"
        description="Quanto você separou e quanto falta para suas metas"
        actions={
          <>
            <Button variant="outline" onClick={() => setDraft({ name: "", goal: "", icon: "piggy-bank" })}>
              <Plus /> Nova categoria
            </Button>
            <Button onClick={() => openNew({ kind: "INVESTIMENTO" })}>
              <PiggyBank /> Investir
            </Button>
          </>
        }
      />

      {data.isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-28" />
          <Skeleton className="h-48" />
        </div>
      ) : data.isError || !data.data ? (
        <ErrorState message={errorMessage(data.error)} onRetry={() => data.refetch()} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Card as="div">
              <p className="text-sm text-muted-foreground">Investido em {monthName(month)}</p>
              <Money cents={data.data.monthTotal} className="block text-2xl font-bold" />
            </Card>
            <Card as="div">
              <p className="text-sm text-muted-foreground">Total acumulado</p>
              <Money cents={data.data.accumulatedTotal} className="block text-2xl font-bold" />
            </Card>
          </div>

          {data.data.categories.length === 0 ? (
            <Card>
              <EmptyState
                icon={<PiggyBank className="size-7" />}
                title="Crie sua primeira categoria de investimento"
                actions={<Button onClick={() => setDraft({ name: "", goal: "", icon: "piggy-bank" })}><Plus /> Criar categoria</Button>}
              >
                Ex.: Reserva de emergência, Viagem, Imóvel. Você pode definir uma meta para acompanhar o progresso.
              </EmptyState>
            </Card>
          ) : (
            <ul className="grid grid-cols-1 gap-3 lg:grid-cols-2">
              {data.data.categories.map((c) => {
                const goal = c.goalCents ?? 0;
                const pct = goal > 0 ? Math.min(100, Math.round((c.accumulatedCents / goal) * 100)) : 0;
                return (
                  <li key={c.categoryId}>
                    <Card as="article" className="space-y-3">
                      <div className="flex items-start gap-3">
                        <CategoryIcon icon={c.icon} color={c.color} />
                        <div className="min-w-0 flex-1">
                          <h2 className="truncate font-semibold">{c.name}</h2>
                          <p className="text-xs text-muted-foreground">
                            Neste mês: <Money cents={c.monthCents} />
                          </p>
                        </div>
                        <Button variant="ghost" size="icon" onClick={() => edit(c)} aria-label={`Editar ${c.name}`}>
                          <Pencil />
                        </Button>
                      </div>
                      <div className="flex items-end justify-between gap-3">
                        <div>
                          <p className="text-xs text-muted-foreground">Acumulado</p>
                          <Money cents={c.accumulatedCents} className="text-xl font-bold" />
                        </div>
                        <Button size="sm" onClick={() => openNew({ kind: "INVESTIMENTO", categoryId: c.categoryId })}>
                          <Plus /> Investir
                        </Button>
                      </div>
                      {goal > 0 ? (
                        <div className="space-y-1.5">
                          <Progress value={c.accumulatedCents} max={goal} label={`${pct}% da meta de ${c.name}`} />
                          <p className="flex justify-between text-xs text-muted-foreground">
                            <span>{pct}% da meta de {formatBRL(goal)}</span>
                            <span>
                              {c.accumulatedCents >= goal ? "Meta alcançada!" : `Faltam ${formatBRL(goal - c.accumulatedCents)}`}
                            </span>
                          </p>
                        </div>
                      ) : (
                        <Button variant="link" onClick={() => edit(c)}>
                          <Target /> Definir uma meta (quanto quer juntar)
                        </Button>
                      )}
                    </Card>
                  </li>
                );
              })}
            </ul>
          )}

          <Card aria-labelledby="historico">
            <CardTitle id="historico">Histórico dos últimos 12 meses</CardTitle>
            <Bars
              title="Valor investido por mês nos últimos 12 meses"
              data={data.data.history}
              series={[{ key: "investido", label: "Investido", color: "series-invest" }]}
            />
          </Card>
          <p className="text-center text-xs text-muted-foreground">
            A Fynavero organiza o que você investe, mas não faz recomendação de investimento.
          </p>
        </>
      )}

      <Modal
        open={!!draft}
        onOpenChange={(o) => !o && setDraft(null)}
        title={draft?.id ? "Editar categoria de investimento" : "Nova categoria de investimento"}
        footer={
          <Button onClick={save} disabled={saving || !draft?.name.trim()}>
            {saving && <Spinner />} {draft?.id ? "Salvar alterações" : "Criar categoria"}
          </Button>
        }
      >
        {draft && (
          <div className="space-y-4">
            <Field label="Nome">
              <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={60} placeholder="Ex.: Viagem" />
            </Field>
            <Field
              label="Meta: quanto você quer juntar (opcional)"
              hint="Este campo não registra investimento. Para lançar um valor investido, use o botão “Investir” no cartão da categoria."
            >
              <CurrencyInput value={draft.goal} onChange={(goal) => setDraft({ ...draft, goal })} />
            </Field>
            <IconPicker value={draft.icon} onChange={(icon) => setDraft({ ...draft, icon })} />
          </div>
        )}
      </Modal>
    </div>
  );
}
