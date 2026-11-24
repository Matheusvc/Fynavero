import { Link } from "react-router";
import { Target } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { GoalProgress } from "@/features/goals/GoalCard";
import { useGoals, useMe } from "@/lib/queries";

/** Objetivos no Início. Sem objetivo, convida a criar quem escolheu "juntar" ou "investir" no primeiro acesso. */
export function GoalsCard() {
  const goals = useGoals();
  const me = useMe();
  if (!goals.data) return null;

  const active = goals.data.filter((g) => g.status !== "CONCLUIDO");
  if (goals.data.length === 0) {
    const wants = me.data?.objectives.some((o) => o === "OBJETIVO" || o === "INVESTIR");
    if (!wants) return null;
    return (
      <Card className="flex flex-wrap items-center gap-3">
        <Target className="size-5 shrink-0 text-primary-text" aria-hidden="true" />
        <p className="min-w-48 flex-1 text-sm">Você quer juntar dinheiro. Crie um objetivo com valor e prazo e acompanhe o progresso aqui.</p>
        <Button size="sm" asChild>
          <Link to="/objetivos">Criar objetivo</Link>
        </Button>
      </Card>
    );
  }

  const shown = (active.length ? active : goals.data).slice(0, 3);
  return (
    <Card aria-labelledby="objetivos">
      <CardTitle id="objetivos">Seus objetivos</CardTitle>
      <ul className="space-y-5">
        {shown.map((g) => (
          <li key={g.id}>
            <GoalProgress g={g} compact />
          </li>
        ))}
      </ul>
      <Link to="/objetivos" className="mt-4 inline-block text-sm font-medium text-primary-text underline-offset-4 hover:underline">
        Ver objetivos
      </Link>
    </Card>
  );
}
