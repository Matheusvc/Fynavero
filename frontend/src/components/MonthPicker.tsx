import { ChevronLeft, ChevronRight } from "lucide-react";
import { currentMonth, monthLabel } from "@/lib/format";
import { useMonth } from "@/lib/month";
import { cn } from "@/lib/utils";

/** "‹ Novembro 2026 ›" */
export function MonthPicker({ className }: { className?: string }) {
  const { month, prev, next, setMonth } = useMonth();
  const isCurrent = month === currentMonth();
  return (
    <div className={cn("flex items-center gap-1", className)} role="group" aria-label="Mês selecionado">
      <button
        type="button"
        onClick={prev}
        className="grid size-touch place-items-center rounded-lg hover:bg-muted"
        aria-label="Mês anterior"
      >
        <ChevronLeft className="size-5" aria-hidden="true" />
      </button>
      <button
        type="button"
        onClick={() => setMonth(currentMonth())}
        className="min-h-touch rounded-lg px-1 text-center text-sm font-semibold whitespace-nowrap hover:bg-muted sm:min-w-36 sm:px-2 sm:text-base"
        aria-live="polite"
        title={isCurrent ? undefined : "Voltar para o mês atual"}
      >
        {monthLabel(month)}
      </button>
      <button
        type="button"
        onClick={next}
        className="grid size-touch place-items-center rounded-lg hover:bg-muted"
        aria-label="Próximo mês"
      >
        <ChevronRight className="size-5" aria-hidden="true" />
      </button>
    </div>
  );
}
