import { useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Money } from "./Money";
import { ViewToggle } from "./ui/misc";
import { Icon } from "@/lib/icons";
import { formatBRL, formatPct } from "@/lib/format";
import type { Slice } from "@/lib/types";

const compact = new Intl.NumberFormat("pt-BR", { notation: "compact", style: "currency", currency: "BRL", maximumFractionDigits: 1 });
const axisMoney = (cents: number) => compact.format(cents / 100).replace(/ /g, " ");

function TooltipBox({ title, rows }: { title?: string; rows: { label: string; cents: number; color?: string; pct?: string }[] }) {
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-sm text-popover-foreground shadow-md">
      {title && <p className="mb-1 font-semibold">{title}</p>}
      {rows.map((r) => (
        <p key={r.label} className="flex items-center gap-2">
          {r.color && <span className="size-2.5 rounded-sm" style={{ background: `var(--${r.color})` }} aria-hidden="true" />}
          <span className="text-muted-foreground">{r.label}</span>
          <span className="money ml-auto pl-3 font-semibold">{formatBRL(r.cents)}</span>
          {r.pct && <span className="text-muted-foreground">({r.pct})</span>}
        </p>
      ))}
    </div>
  );
}

/** Rosca de composição (no máximo 6 categorias + "Outros") com lista ao lado. */
export function Donut({ slices, total, title }: { slices: Slice[]; total: number; title: string }) {
  const [table, setTable] = useState(false);
  return (
    <div>
      <div className="mb-2 flex justify-end">
        <ViewToggle table={table} onChange={setTable} />
      </div>
      {table ? (
        <table className="w-full text-sm">
          <caption className="sr-only">{title}</caption>
          <thead>
            <tr className="border-b text-left text-muted-foreground">
              <th scope="col" className="py-2 font-medium">Categoria</th>
              <th scope="col" className="py-2 text-right font-medium">Valor</th>
              <th scope="col" className="py-2 text-right font-medium">%</th>
            </tr>
          </thead>
          <tbody>
            {slices.map((s) => (
              <tr key={s.name} className="border-b last:border-0">
                <th scope="row" className="py-2 text-left font-normal">{s.name}</th>
                <td className="py-2 text-right"><Money cents={s.amountCents} /></td>
                <td className="money py-2 text-right">{formatPct(s.bps)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold">
              <th scope="row" className="py-2 text-left">Total</th>
              <td className="py-2 text-right"><Money cents={total} /></td>
              <td className="money py-2 text-right">100%</td>
            </tr>
          </tfoot>
        </table>
      ) : (
        <div className="grid grid-cols-1 items-center gap-4 sm:grid-cols-[10rem_minmax(0,1fr)]">
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
                <p className="money text-sm font-bold">{formatBRL(total)}</p>
              </div>
            </div>
          </div>
          <ul className="space-y-1" aria-label={title}>
            {slices.map((s) => (
              <li key={s.name} className="flex min-h-9 items-center gap-2 text-sm">
                <span className="size-3 shrink-0 rounded-sm" style={{ background: `var(--${s.color})` }} aria-hidden="true" />
                <Icon name={s.icon} className="size-4 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1 truncate">{s.name}</span>
                <Money cents={s.amountCents} className="font-medium" />
                <span className="money w-12 text-right text-muted-foreground">{formatPct(s.bps)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export type Series<T> = { key: keyof T & string; label: string; color: string; stack?: string };

/** Barras para comparar meses. Um único eixo Y, sempre em reais. */
export function Bars<T extends { label: string }>({
  data,
  series,
  title,
  height = 240,
}: {
  data: T[];
  series: Series<T>[];
  title: string;
  height?: number;
}) {
  const [table, setTable] = useState(false);
  return (
    <div>
      <div className="mb-2 flex justify-end">
        <ViewToggle table={table} onChange={setTable} />
      </div>
      {table ? (
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">{title}</caption>
            <thead>
              <tr className="border-b text-muted-foreground">
                <th scope="col" className="py-2 pr-4 text-left font-medium">Mês</th>
                {series.map((s) => (
                  <th key={s.key} scope="col" className="py-2 pl-4 text-right font-medium">{s.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((d) => (
                <tr key={d.label} className="border-b last:border-0">
                  <th scope="row" className="py-2 pr-4 text-left font-normal">{d.label}</th>
                  {series.map((s) => (
                    <td key={s.key} className="py-2 pl-4 text-right"><Money cents={Number(d[s.key])} /></td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div style={{ height }} role="img" aria-label={`${title}. Use “Ver como tabela” para os valores.`}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: 0 }} barCategoryGap="22%">
              <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: "var(--muted-foreground)", fontSize: "var(--type-xs)" }} />
              <YAxis
                tickFormatter={axisMoney}
                tickLine={false}
                axisLine={false}
                width={72}
                tick={{ fill: "var(--muted-foreground)", fontSize: "var(--type-xs)" }}
              />
              <Tooltip
                cursor={{ fill: "var(--muted)" }}
                content={({ active, payload, label }) => {
                  if (!active || !payload?.length) return null;
                  return (
                    <TooltipBox
                      title={String(label)}
                      rows={series.map((s) => ({
                        label: s.label,
                        cents: Number((payload[0]!.payload as T)[s.key]),
                        color: s.color,
                      }))}
                    />
                  );
                }}
              />
              <Legend
                iconType="square"
                formatter={(v) => <span className="text-sm text-foreground">{v}</span>}
                wrapperStyle={{ paddingTop: 8 }}
              />
              {series.map((s) => (
                <Bar
                  key={s.key}
                  dataKey={s.key as never}
                  name={s.label}
                  fill={`var(--${s.color})`}
                  stackId={s.stack}
                  isAnimationActive={false}
                />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
