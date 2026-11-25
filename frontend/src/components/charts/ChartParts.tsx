import { ChartPie, Table2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatBRL } from "@/lib/format";
import { HIDDEN_MONEY, valuesHidden } from "@/lib/privacy";

const compact = new Intl.NumberFormat("pt-BR", { notation: "compact", style: "currency", currency: "BRL", maximumFractionDigits: 1 });

/** Rótulo curto para o eixo Y ("R$ 4,5 mil"). */
export const axisMoney = (cents: number) => valuesHidden() ? "•••" : compact.format(cents / 100).replace(/ /g, " ");

export type TooltipRow = { label: string; cents: number; color?: string; pct?: string };

/** Caixa de tooltip dos gráficos, sempre com valor em R$ (e % quando houver). */
export function TooltipBox({ title, rows }: { title?: string; rows: TooltipRow[] }) {
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md">
      {title && <p className="mb-1 font-semibold">{title}</p>}
      {rows.map((r) => (
        <p key={r.label} className="flex items-center gap-2">
          {r.color && <span className="size-2.5 rounded-sm" style={{ background: `var(--${r.color})` }} aria-hidden="true" />}
          <span className="text-muted-foreground">{r.label}</span>
          <span className="money ml-auto pl-3 font-semibold">{valuesHidden() ? HIDDEN_MONEY : formatBRL(r.cents)}</span>
          {r.pct && <span className="text-muted-foreground">({r.pct})</span>}
        </p>
      ))}
    </div>
  );
}

/** Alterna entre gráfico e tabela ("Ver como tabela"). */
export function ViewToggle({ table, onChange }: { table: boolean; onChange: (v: boolean) => void }) {
  return (
    <Button variant="ghost" size="sm" onClick={() => onChange(!table)} aria-pressed={table}>
      {table ? <ChartPie /> : <Table2 />}
      {table ? "Ver gráfico" : "Ver como tabela"}
    </Button>
  );
}
