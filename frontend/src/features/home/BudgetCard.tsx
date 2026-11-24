import { Link } from "react-router";
import { Gauge } from "lucide-react";
import { Money } from "@/components/Money";
import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { CategoryIcon } from "@/lib/icons";
import { useBudgets } from "@/lib/queries";
import type { Budget } from "@/lib/types";

export const budgetTone = (b: Budget) => (b.level === "ESTOURADO" ? "negative" : b.level === "ALERTA" ? "warning" : "primary");

/** Limites do mês por categoria. Sem nenhum limite definido, mostra um convite discreto para criar. */
export function BudgetCard({ month }: { month: string }) {
  const budgets = useBudgets(month);
  if (!budgets.data) return null;

  if (budgets.data.length === 0) {
    return (
      <Card className="flex items-center gap-3">
        <Gauge className="size-5 shrink-0 text-primary-text" aria-hidden="true" />
        <p className="flex-1 text-sm">
          Quer um aviso antes de estourar o orçamento?{" "}
          <Link to="/configuracoes#categorias" className="font-medium text-primary-text underline-offset-4 hover:underline">
            Defina um limite por categoria
          </Link>
          .
        </p>
      </Card>
    );
  }

  return (
    <Card aria-labelledby="limites">
      <CardTitle id="limites">Limites do mês</CardTitle>
      <ul className="space-y-4">
        {budgets.data.slice(0, 6).map((b) => (
          <li key={b.categoryId}>
            <div className="mb-1.5 flex items-center gap-2 text-sm">
              <CategoryIcon icon={b.icon} color={b.color} size="sm" />
              <span className="min-w-0 flex-1 truncate font-medium">{b.name}</span>
              {b.level === "ESTOURADO" && <Badge tone="negative">Passou do limite</Badge>}
              {b.level === "ALERTA" && <Badge tone="brand">{Math.floor(b.pctBps / 100)}%</Badge>}
            </div>
            <Progress value={b.spentCents} max={b.limitCents} label={`${b.name}: gasto em relação ao limite`} tone={budgetTone(b)} />
            <p className="mt-1 text-xs text-muted-foreground">
              <Money cents={b.spentCents} /> de <Money cents={b.limitCents} />
              {b.spentCents < b.limitCents ? (
                <> · ainda pode gastar <Money cents={b.limitCents - b.spentCents} /></>
              ) : (
                <> · <Money cents={b.spentCents - b.limitCents} /> acima</>
              )}
            </p>
          </li>
        ))}
      </ul>
      <Link to="/configuracoes#categorias" className="mt-4 inline-block text-sm font-medium text-primary-text underline-offset-4 hover:underline">
        Ajustar limites
      </Link>
    </Card>
  );
}
