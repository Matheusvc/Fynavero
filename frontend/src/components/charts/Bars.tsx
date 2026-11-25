import { useState } from "react";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Money } from "@/components/Money";
import { useHideValues } from "@/lib/privacy";
import { axisMoney, TooltipBox, ViewToggle } from "./ChartParts";

export type Series<T> = { key: keyof T & string; label: string; color: string; stack?: string };

const TICK = { fill: "var(--muted-foreground)", fontSize: "var(--type-xs)" };

/** Barras para comparar meses. Um único eixo Y, sempre em reais; com "Ver como tabela". */
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
  useHideValues(); // redesenha o eixo ao ocultar/mostrar valores
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
              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={TICK} />
              <YAxis tickFormatter={axisMoney} tickLine={false} axisLine={false} width={72} tick={TICK} />
              <Tooltip
                cursor={{ fill: "var(--muted)" }}
                content={({ active, payload, label }) => {
                  if (!active || !payload?.length) return null;
                  const row = payload[0]!.payload as T;
                  return (
                    <TooltipBox
                      title={String(label)}
                      rows={series.map((s) => ({ label: s.label, cents: Number(row[s.key]), color: s.color }))}
                    />
                  );
                }}
              />
              <Legend iconType="square" formatter={(v) => <span className="text-sm text-foreground">{v}</span>} wrapperStyle={{ paddingTop: 8 }} />
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
