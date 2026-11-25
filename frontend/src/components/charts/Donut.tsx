import { useState } from "react";
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { Pencil } from "lucide-react";
import { Money } from "@/components/Money";
import { Button } from "@/components/ui/button";
import { Icon } from "@/lib/icons";
import { formatPct } from "@/lib/format";
import type { Slice } from "@/lib/types";
import { TooltipBox, ViewToggle } from "./ChartParts";

/**
 * Rosca de composição (no máximo 6 categorias + "Outros") com a lista ao lado, ou tabela.
 * Com {@code onEdit}, cada linha vira um botão para editar os lançamentos daquela categoria.
 */
export function Donut({
  slices,
  total,
  title,
  onEdit,
}: {
  slices: Slice[];
  total: number;
  title: string;
  onEdit?: (s: Slice) => void;
}) {
  const [table, setTable] = useState(false);
  return (
    <div>
      <div className="mb-2 flex justify-end">
        <ViewToggle table={table} onChange={setTable} />
      </div>
      {table ? (
        <DonutTable slices={slices} total={total} title={title} onEdit={onEdit} />
      ) : (
        <div className="@container">
          <div className="grid grid-cols-1 items-center gap-4 @[30rem]:grid-cols-[10rem_minmax(0,1fr)]">
            <DonutChart slices={slices} total={total} />
            <DonutLegend slices={slices} title={title} onEdit={onEdit} />
          </div>
        </div>
      )}
    </div>
  );
}

function DonutChart({ slices, total }: { slices: Slice[]; total: number }) {
  return (
    <div className="relative mx-auto aspect-square w-full max-w-48" aria-hidden="true">
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie
            data={slices}
            dataKey="amountCents"
            nameKey="name"
            innerRadius="62%"
            outerRadius="100%"
            paddingAngle={1}
            stroke="var(--card)"
            strokeWidth={2}
            isAnimationActive={false}
          >
            {slices.map((s) => (
              <Cell key={s.name} fill={`var(--${s.color})`} />
            ))}
          </Pie>
          <Tooltip
            content={({ active, payload }) => {
              const p = payload?.[0]?.payload as Slice | undefined;
              if (!active || !p) return null;
              return <TooltipBox rows={[{ label: p.name, cents: p.amountCents, color: p.color, pct: formatPct(p.bps) }]} />;
            }}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
        <div>
          <p className="text-xs text-muted-foreground">Total</p>
          <Money cents={total} className="block text-sm font-bold" />
        </div>
      </div>
    </div>
  );
}

function DonutLegend({ slices, title, onEdit }: { slices: Slice[]; title: string; onEdit?: (s: Slice) => void }) {
  return (
    <ul className="space-y-0.5" aria-label={title}>
      {slices.map((s) => {
        const content = (
          <>
            <span className="size-3 shrink-0 rounded-sm" style={{ background: `var(--${s.color})` }} aria-hidden="true" />
            <Icon name={s.icon} className="size-4 shrink-0 text-muted-foreground" />
            <span className="min-w-0 flex-1 truncate">{s.name}</span>
            <Money cents={s.amountCents} className="font-medium" />
            <span className="money w-12 shrink-0 text-right text-muted-foreground">{formatPct(s.bps)}</span>
          </>
        );
        return (
          <li key={s.name}>
            {onEdit ? (
              <button
                type="button"
                onClick={() => onEdit(s)}
                className="-mx-2 flex min-h-touch w-[calc(100%+1rem)] items-center gap-2 rounded-lg px-2 text-left text-sm hover:bg-muted"
              >
                {content}
                <Pencil className="size-3.5 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="sr-only">Editar {s.name}</span>
              </button>
            ) : (
              <div className="flex min-h-9 items-center gap-2 text-sm">{content}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function DonutTable({
  slices,
  total,
  title,
  onEdit,
}: {
  slices: Slice[];
  total: number;
  title: string;
  onEdit?: (s: Slice) => void;
}) {
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">{title}</caption>
      <thead>
        <tr className="border-b text-left text-muted-foreground">
          <th scope="col" className="py-2 font-medium">Categoria</th>
          <th scope="col" className="py-2 text-right font-medium">Valor</th>
          <th scope="col" className="py-2 text-right font-medium">%</th>
          {onEdit && (
            <th scope="col" className="py-2 text-right font-medium">
              <span className="sr-only">Ações</span>
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {slices.map((s) => (
          <tr key={s.name} className="border-b last:border-0">
            <th scope="row" className="py-2 text-left font-normal">{s.name}</th>
            <td className="py-2 text-right"><Money cents={s.amountCents} /></td>
            <td className="money py-2 text-right">{formatPct(s.bps)}</td>
            {onEdit && (
              <td className="py-1 pl-2 text-right">
                <Button variant="ghost" size="sm" onClick={() => onEdit(s)} aria-label={`Editar ${s.name}`}>
                  <Pencil /> Editar
                </Button>
              </td>
            )}
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr className="font-semibold">
          <th scope="row" className="py-2 text-left">Total</th>
          <td className="py-2 text-right"><Money cents={total} /></td>
          <td className="money py-2 text-right">100%</td>
          {onEdit && <td />}
        </tr>
      </tfoot>
    </table>
  );
}
