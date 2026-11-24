import { type ReactNode } from "react";
import { ArrowDownRight, ArrowUpRight, Minus, TriangleAlert, Wallet } from "lucide-react";
import { Money } from "@/components/Money";
import { Card } from "@/components/ui/card";
import { changeBps, formatPct, monthName, shiftMonth } from "@/lib/format";
import type { Totals } from "@/lib/types";

function Change({ current, previous, month }: { current: number; previous: number; month: string }) {
  const bps = changeBps(current, previous);
  const prevName = monthName(shiftMonth(month, -1));
  if (bps === null) return <p className="text-xs text-muted-foreground">Sem dados de {prevName}</p>;
  const I = bps > 0 ? ArrowUpRight : bps < 0 ? ArrowDownRight : Minus;
  const word = bps > 0 ? "a mais" : bps < 0 ? "a menos" : "igual";
  return (
    <p className="flex items-center gap-1 text-xs text-muted-foreground">
      <I className="size-3.5" aria-hidden="true" />
      <span>
        {bps === 0 ? `Igual a ${prevName}` : `${formatPct(Math.abs(bps))} ${word} que em ${prevName}`}
      </span>
    </p>
  );
}

export function StatCard({ label, cents, prev, month, note }: { label: string; cents: number; prev: number; month: string; note?: ReactNode }) {
  return (
    <Card as="div" className="space-y-1">
      <p className="text-sm text-muted-foreground">{label}</p>
      <Money cents={cents} className="block text-2xl font-bold" />
      {note && <p className="text-xs text-muted-foreground">{note}</p>}
      <Change current={cents} previous={prev} month={month} />
    </Card>
  );
}

export function Hero({ t, month }: { t: Totals; month: string }) {
  const negative = t.saldo < 0;
  return (
    <section
      aria-labelledby="saldo"
      className="relative overflow-hidden rounded-xl bg-secondary p-5 text-secondary-foreground shadow-md sm:p-7"
    >
      <span className="absolute inset-y-0 left-0 w-1.5 bg-primary" aria-hidden="true" />
      <h2 id="saldo" className="flex items-center gap-2 text-sm font-medium opacity-80">
        <Wallet className="size-4" aria-hidden="true" /> Saldo para passar o mês · {monthName(month)}
      </h2>
      <p className="mt-2 text-hero font-bold">
        <Money cents={t.saldo} />
      </p>
      {negative && (
        <p className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-negative px-2 py-1 text-sm font-semibold text-negative-foreground">
          <TriangleAlert className="size-4" aria-hidden="true" /> Saldo negativo
        </p>
      )}
      <p className="mt-3 text-sm opacity-80">
        Sobra do mês <Money cents={t.sobra} className="font-semibold" /> − Investido{" "}
        <Money cents={t.investido} className="font-semibold" />
      </p>
    </section>
  );
}
