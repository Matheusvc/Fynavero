import type { ReactNode } from "react";
import { Money } from "@/components/Money";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { formatDate, formatPct } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import type { Goal, GoalStatus } from "@/lib/types";

const STATUS: Record<GoalStatus, { label: string; tone: "positive" | "brand" | "negative" | "neutral" }> = {
  CONCLUIDO: { label: "Concluído", tone: "positive" },
  NO_RITMO: { label: "No ritmo", tone: "positive" },
  ATENCAO: { label: "Guarde mais este mês", tone: "brand" },
  PRAZO_VENCIDO: { label: "Prazo vencido", tone: "negative" },
  SEM_PRAZO: { label: "Sem prazo", tone: "neutral" },
};

/** Linha de orientação: quanto guardar por mês, quanto falta, ou parabéns. */
function goalGuidance(g: Goal): ReactNode {
  if (g.status === "CONCLUIDO") return "Você chegou lá! Objetivo concluído.";
  if (g.status === "PRAZO_VENCIDO") return <>O prazo passou. Faltam <Money cents={g.remainingCents} /> — edite o prazo para recalcular.</>;
  if (g.status === "SEM_PRAZO") return <>Faltam <Money cents={g.remainingCents} />. Defina um prazo para saber quanto guardar por mês.</>;
  const need = g.monthlyNeededCents ?? 0;
  const missing = Math.max(0, need - g.savedThisMonthCents);
  return missing > 0 ? (
    <>
      Guarde <Money cents={need} />/mês para chegar no prazo. Neste mês faltam <Money cents={missing} />.
    </>
  ) : (
    <>
      Neste mês você já guardou o necessário (<Money cents={need} />). Continue assim!
    </>
  );
}

export function GoalProgress({ g, compact = false }: { g: Goal; compact?: boolean }) {
  const s = STATUS[g.status];
  return (
    <div>
      <div className="mb-2 flex items-center gap-3">
        <CategoryIcon icon={g.icon} color={g.color} size={compact ? "sm" : "md"} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">{g.name}</p>
          {!compact && g.deadline && (
            <p className="text-xs text-muted-foreground">
              Até {formatDate(g.deadline)}
              {g.monthsLeft ? ` · ${g.monthsLeft === 1 ? "último mês" : `${g.monthsLeft} meses`}` : ""}
            </p>
          )}
        </div>
        <Badge tone={s.tone}>{s.label}</Badge>
      </div>
      <Progress value={g.savedCents} max={g.targetCents} label={`${g.name}: guardado em relação à meta`} tone={g.status === "CONCLUIDO" ? "positive" : "primary"} />
      <p className="mt-1.5 flex flex-wrap justify-between gap-x-2 text-sm">
        <span>
          <Money cents={g.savedCents} className="font-semibold" /> <span className="text-muted-foreground">de</span> <Money cents={g.targetCents} />
        </span>
        <span className="money text-muted-foreground">{formatPct(g.pctBps)}</span>
      </p>
      {!compact && <p className="mt-2 text-sm text-muted-foreground">{goalGuidance(g)}</p>}
    </div>
  );
}
