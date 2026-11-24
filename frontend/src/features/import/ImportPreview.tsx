import { Money } from "@/components/Money";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import { type ParsedRow } from "@/features/import/parser";
import { KIND_LABEL, KIND_PLURAL } from "@/lib/labels";

export function SummaryList({ rec, des, inv }: { rec: number; des: number; inv: number }) {
  return (
    <dl className="grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
      {([["RECEITA", rec], ["DESPESA", des], ["INVESTIMENTO", inv]] as const).map(([k, v]) => (
        <div key={k} className="rounded-lg bg-muted p-3">
          <dt className="text-muted-foreground">{KIND_PLURAL[k]}</dt>
          <dd><Money cents={v} className="text-lg font-bold" /></dd>
        </div>
      ))}
    </dl>
  );
}

export function PreviewTable({ rows, dupes }: { rows: ParsedRow[]; dupes: Set<string> }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-max text-sm">
        <thead>
          <tr className="border-b text-left text-muted-foreground">
            <th scope="col" className="py-2 pr-3 font-medium">Data</th>
            <th scope="col" className="py-2 pr-3 font-medium">Descrição</th>
            <th scope="col" className="py-2 pr-3 font-medium">Categoria</th>
            <th scope="col" className="py-2 pr-3 font-medium">Tipo</th>
            <th scope="col" className="py-2 text-right font-medium">Valor</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key} className="border-b last:border-0">
              <td className="money py-2 pr-3">{r.date ? formatDate(r.date) : "—"}</td>
              <td className="py-2 pr-3">
                {r.description || "—"}
                {r.installmentNumber && <Badge className="ml-2">{r.installmentNumber}/{r.installmentTotal}</Badge>}
                {dupes.has(r.key) && <Badge tone="forecast" className="ml-2">possível duplicado</Badge>}
              </td>
              <td className="py-2 pr-3">{r.categoryLabel}</td>
              <td className="py-2 pr-3">{KIND_LABEL[r.kind]}</td>
              <td className="py-2 text-right"><Money cents={r.amountCents!} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
