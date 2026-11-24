import { useState } from "react";
import { toast } from "sonner";
import { Pencil, PiggyBank, Plus, Target, Trash2 } from "lucide-react";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Modal } from "@/components/ui/dialog";
import { EmptyState, ErrorState, Skeleton, Spinner } from "@/components/ui/feedback";
import { PageHeader } from "@/components/ui/page-header";
import { Money } from "@/components/Money";
import { errorMessage } from "@/lib/api";
import { useGoalMutations, useGoals } from "@/lib/queries";
import type { Goal } from "@/lib/types";
import { GoalDialog } from "./GoalDialog";
import { GoalProgress } from "./GoalCard";

/** Objetivos de economia: viagem, reserva, carro… com meta, prazo, progresso e quanto guardar por mês. */
export default function GoalsPage() {
  const goals = useGoals();
  const m = useGoalMutations();
  const { openNew } = useQuickAdd();
  const [dialog, setDialog] = useState<{ goal: Goal | null; key: number } | null>(null);
  const [removing, setRemoving] = useState<Goal | null>(null);

  const open = (goal: Goal | null) => setDialog({ goal, key: Date.now() });
  const totals = (goals.data ?? []).reduce((a, g) => ({ saved: a.saved + g.savedCents, target: a.target + g.targetCents }), { saved: 0, target: 0 });

  return (
    <div>
      <PageHeader
        title="Objetivos"
        description="Junte dinheiro para o que importa e acompanhe quanto falta."
        actions={
          <Button onClick={() => open(null)}>
            <Plus /> Novo objetivo
          </Button>
        }
      />

      {goals.isLoading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2" aria-busy="true" aria-label="Carregando">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      ) : goals.isError ? (
        <ErrorState message={errorMessage(goals.error)} onRetry={() => goals.refetch()} />
      ) : !goals.data?.length ? (
        <Card>
          <EmptyState
            icon={<Target className="size-7" />}
            title="Qual é o seu próximo objetivo?"
            actions={
              <Button onClick={() => open(null)}>
                <Plus /> Criar objetivo
              </Button>
            }
          >
            Uma viagem, a reserva de emergência, a entrada do carro… Diga quanto quer juntar e até quando: mostramos quanto guardar por mês
            e o quanto você já avançou.
          </EmptyState>
        </Card>
      ) : (
        <>
          {goals.data.length > 1 && (
            <p className="mb-4 text-sm text-muted-foreground">
              Guardado em todos os objetivos: <Money cents={totals.saved} className="font-semibold text-foreground" /> de{" "}
              <Money cents={totals.target} />
            </p>
          )}
          <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {goals.data.map((g) => (
              <li key={g.id}>
                <Card className="flex h-full flex-col gap-4">
                  <GoalProgress g={g} />
                  <div className="mt-auto flex flex-wrap gap-2">
                    {g.status !== "CONCLUIDO" && (
                      <Button size="sm" onClick={() => openNew({ kind: "INVESTIMENTO", categoryId: g.id })}>
                        <PiggyBank /> Guardar dinheiro
                      </Button>
                    )}
                    <Button size="sm" variant="outline" onClick={() => open(g)}>
                      <Pencil /> Editar
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setRemoving(g)} aria-label={`Deixar de acompanhar ${g.name}`}>
                      <Trash2 />
                    </Button>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        </>
      )}

      {dialog && <GoalDialog key={dialog.key} goal={dialog.goal} open onClose={() => setDialog(null)} />}

      <Modal
        open={!!removing}
        onOpenChange={(o) => !o && setRemoving(null)}
        title="Deixar de acompanhar este objetivo?"
        footer={
          <>
            <Button variant="outline" onClick={() => setRemoving(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={m.remove.isPending}
              onClick={() =>
                removing &&
                m.remove.mutate(removing.id, {
                  onSuccess: () => {
                    toast.success("Objetivo removido. O que você guardou continua em Investimentos.");
                    setRemoving(null);
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              {m.remove.isPending && <Spinner />} Deixar de acompanhar
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">
          A meta e o prazo saem desta tela. A categoria “{removing?.name}” e tudo o que você já guardou continuam em Investimentos.
        </p>
      </Modal>
    </div>
  );
}
