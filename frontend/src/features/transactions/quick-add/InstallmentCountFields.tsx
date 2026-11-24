import { Field, Input } from "@/components/ui/field";
import { monthLabel, monthsBetween, shiftMonth } from "@/lib/format";

/** Número de parcelas e mês da última parcela, ligados: mudar um recalcula o outro. */
export function InstallmentCountFields({
  firstMonth,
  count,
  onCount,
  error,
  min = 2,
  label = "Número de parcelas",
}: {
  firstMonth: string;
  count: string;
  onCount: (v: string) => void;
  error?: string;
  min?: number;
  label?: string;
}) {
  const n = Number(count);
  const lastMonth = Number.isInteger(n) && n >= 1 ? shiftMonth(firstMonth, n - 1) : "";
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Field label={label} error={error}>
        <Input type="number" inputMode="numeric" min={min} max={120} value={count} onChange={(e) => onCount(e.target.value)} />
      </Field>
      <Field label="Mês da última parcela" hint={lastMonth ? monthLabel(lastMonth) : undefined}>
        <Input
          type="month"
          min={shiftMonth(firstMonth, min - 1)}
          value={lastMonth}
          onChange={(e) => {
            if (!e.target.value) return;
            onCount(String(Math.max(min, monthsBetween(firstMonth, e.target.value) + 1)));
          }}
        />
      </Field>
    </div>
  );
}
