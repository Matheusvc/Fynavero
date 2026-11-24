import { useState } from "react";
import { ChevronLeft, ChevronRight, Download, FileText } from "lucide-react";
import { Money } from "@/components/Money";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { ErrorState, Skeleton } from "@/components/ui/feedback";
import { PageHeader } from "@/components/ui/page-header";
import { Segmented } from "@/components/ui/segmented";
import { errorMessage } from "@/lib/api";
import { monthLabel } from "@/lib/format";
import { useMonth } from "@/lib/month";
import { useAnnualReport, useCompareReport } from "@/lib/queries";
import type { AnnualReport, ReportRow } from "@/lib/types";
import { cn } from "@/lib/utils";

function Row({ row, strong, total }: { row: ReportRow; strong?: boolean; total?: boolean }) {
  return (
    <tr className={cn("border-b", total && "border-t-2 border-t-foreground/20")}>
      <th
        scope="row"
        className={cn(
          "sticky left-0 z-10 max-w-48 truncate bg-card py-2 pr-3 pl-4 text-left",
          strong ? "font-semibold" : "font-normal",
        )}
      >
        {row.label}
      </th>
      {row.months.map((v, i) => (
        <td key={i} className={cn("px-2 py-2 text-right", v === 0 && "text-muted-foreground")}>
          <Money cents={v} className={strong ? "font-semibold" : ""} />
        </td>
      ))}
      <td className="bg-muted px-2 py-2 text-right font-semibold">
        <Money cents={row.total} />
      </td>
      <td className="px-2 py-2 pr-4 text-right">
        <Money cents={row.average} className={strong ? "font-semibold" : ""} />
      </td>
    </tr>
  );
}

function Section({ title }: { title: string }) {
  return (
    <tr>
      <th colSpan={15} scope="colgroup" className="sticky left-0 bg-accent px-4 py-2 text-left text-sm font-bold">
        {title}
      </th>
    </tr>
  );
}

function AnnualTable({ r }: { r: AnnualReport }) {
  return (
    <div className="overflow-x-auto rounded-xl border bg-card shadow-sm">
      <table className="w-full min-w-max text-sm">
        <caption className="sr-only">Relatório anual de {r.year}: categorias por mês, com total e média mensal</caption>
        <thead>
          <tr className="border-b text-muted-foreground">
            <th scope="col" className="sticky left-0 z-10 bg-card py-2 pr-3 pl-4 text-left font-medium">Categoria</th>
            {r.monthLabels.map((m) => (
              <th key={m} scope="col" className="px-2 py-2 text-right font-medium">{m}</th>
            ))}
            <th scope="col" className="bg-muted px-2 py-2 text-right font-semibold text-foreground">Total</th>
            <th scope="col" className="px-2 py-2 pr-4 text-right font-medium">Média mensal</th>
          </tr>
        </thead>
        <tbody>
          <Section title="Receitas" />
          {r.receitas.length === 0 && <EmptyRow />}
          {r.receitas.map((x) => <Row key={x.label} row={x} />)}
          {r.emprestimos.length > 0 && (
            <>
              <Section title="Empréstimos recebidos (entram no saldo)" />
              {r.emprestimos.map((x) => <Row key={`emp-${x.label}`} row={x} />)}
            </>
          )}
          <Section title="Despesas" />
          {r.despesas.length === 0 && <EmptyRow />}
          {r.despesas.map((g) => (
            <GroupRows key={g.group} rows={g.rows} subtotal={g.subtotal} />
          ))}
          <Row row={r.totalDespesas} strong total />
          <Section title="Investimentos" />
          {r.investimentos.length === 0 && <EmptyRow />}
          {r.investimentos.map((x) => <Row key={x.label} row={x} />)}
          <Row row={r.totalInvestido} strong total />
          <Section title="Resultado" />
          {r.resultado.map((x) => <Row key={x.label} row={x} strong />)}
        </tbody>
      </table>
    </div>
  );
}

function GroupRows({ rows, subtotal }: { rows: ReportRow[]; subtotal: ReportRow }) {
  return (
    <>
      {rows.map((x) => <Row key={`${x.categoryId}-${x.label}`} row={x} />)}
      <Row row={subtotal} strong />
    </>
  );
}

function EmptyRow() {
  return (
    <tr className="border-b">
      <td colSpan={15} className="px-4 py-2 text-muted-foreground">Sem lançamentos no ano.</td>
    </tr>
  );
}

function Annual() {
  const { month } = useMonth();
  const [year, setYear] = useState(() => Number(month.slice(0, 4)));
  const r = useAnnualReport(year);
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1" role="group" aria-label="Ano">
          <Button variant="ghost" size="icon" onClick={() => setYear((y) => y - 1)} aria-label="Ano anterior">
            <ChevronLeft />
          </Button>
          <span className="money min-w-16 text-center text-lg font-semibold" aria-live="polite">{year}</span>
          <Button variant="ghost" size="icon" onClick={() => setYear((y) => y + 1)} aria-label="Próximo ano">
            <ChevronRight />
          </Button>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" asChild>
            <a href={`/api/reports/annual/export?year=${year}&format=xlsx`} download>
              <Download /> Exportar Excel
            </a>
          </Button>
          <Button variant="outline" asChild>
            <a href={`/api/reports/annual/export?year=${year}&format=pdf`} download>
              <FileText /> Exportar PDF
            </a>
          </Button>
        </div>
      </div>
      {r.isLoading ? (
        <Skeleton className="h-96" />
      ) : r.isError || !r.data ? (
        <ErrorState message={errorMessage(r.error)} onRetry={() => r.refetch()} />
      ) : (
        <AnnualTable r={r.data} />
      )}
      <p className="text-xs text-muted-foreground">
        A média mensal considera só os meses com valor. Lançamentos previstos estão incluídos nos seus meses.
      </p>
    </div>
  );
}

function Compare() {
  const { month } = useMonth();
  const r = useCompareReport(month);
  if (r.isLoading) return <Skeleton className="h-96" />;
  if (r.isError || !r.data) return <ErrorState message={errorMessage(r.error)} onRetry={() => r.refetch()} />;
  const head = (
    <thead>
      <tr className="border-b text-muted-foreground">
        <th scope="col" className="py-2 pr-3 text-left font-medium">Item</th>
        <th scope="col" className="px-2 py-2 text-right font-semibold text-foreground">{monthLabel(r.data.month)}</th>
        <th scope="col" className="px-2 py-2 text-right font-medium">{monthLabel(r.data.previousMonth)}</th>
        <th scope="col" className="py-2 pl-2 text-right font-medium">Média (12 meses)</th>
      </tr>
    </thead>
  );
  return (
    <div className="space-y-4">
      <Card aria-labelledby="cmp-totais">
        <CardTitle id="cmp-totais">Resumo</CardTitle>
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Comparativo de totais</caption>
            {head}
            <tbody>
              {r.data.totals.map((x) => (
                <tr key={x.label} className="border-b last:border-0">
                  <th scope="row" className="py-2 pr-3 text-left font-medium">{x.label}</th>
                  <td className="px-2 py-2 text-right font-semibold"><Money cents={x.current} /></td>
                  <td className="px-2 py-2 text-right"><Money cents={x.previous} /></td>
                  <td className="py-2 pl-2 text-right"><Money cents={x.average} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Card aria-labelledby="cmp-cat">
        <CardTitle id="cmp-cat">Despesas por categoria</CardTitle>
        {r.data.categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">Sem despesas no período.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-max text-sm">
              <caption className="sr-only">Comparativo de despesas por categoria</caption>
              {head}
              <tbody>
                {r.data.categories.map((x) => {
                  const above = x.average > 0 && x.current > x.average * 1.1;
                  return (
                    <tr key={`${x.categoryId}-${x.label}`} className="border-b last:border-0">
                      <th scope="row" className="py-2 pr-3 text-left font-normal">
                        {x.label}
                        {above && <span className="ml-2 text-xs text-muted-foreground">(acima da média)</span>}
                      </th>
                      <td className="px-2 py-2 text-right font-semibold"><Money cents={x.current} /></td>
                      <td className="px-2 py-2 text-right"><Money cents={x.previous} /></td>
                      <td className="py-2 pl-2 text-right"><Money cents={x.average} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

export default function ReportsPage() {
  const [tab, setTab] = useState<"anual" | "comparativo">("anual");
  return (
    <div>
      <PageHeader title="Relatórios" description="Seu ano mês a mês e a comparação com meses anteriores" />
      <Segmented
        label="Tipo de relatório"
        value={tab}
        onChange={setTab}
        className="mb-4 max-w-sm"
        options={[
          { value: "anual", label: "Anual" },
          { value: "comparativo", label: "Comparativo" },
        ]}
      />
      {tab === "anual" ? <Annual /> : <Compare />}
    </div>
  );
}
