import { useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import Papa from "papaparse";
import { toast } from "sonner";
import { CircleAlert, CircleCheck, Download, FileSpreadsheet, History, Undo2, Upload } from "lucide-react";
import { Money } from "@/components/Money";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Checkbox, Field, Input, Select } from "@/components/ui/field";
import { Badge, Card, CardTitle, PageHeader, Progress, Spinner } from "@/components/ui/misc";
import { api, errorMessage } from "@/lib/api";
import { currentMonth, formatDate } from "@/lib/format";
import {
  analyze,
  categoryKey,
  extractRows,
  FIELD_LABELS,
  fold,
  NO_CATEGORY,
  type ColumnField,
  type ParsedRow,
  type Sheet,
  type SheetPlan,
} from "@/lib/import/parser";
import { KIND_LABEL, KIND_PLURAL } from "@/lib/labels";
import { invalidateFinance, useCategories, useImports } from "@/lib/queries";
import type { Category, ImportBatch, Kind } from "@/lib/types";

const MAX_BYTES = 5 * 1024 * 1024;
const STEPS = ["Enviar", "Entender", "Revisar", "Confirmar"];

async function readFile(file: File): Promise<Sheet[]> {
  if (/\.csv$/i.test(file.name)) {
    const buf = await file.arrayBuffer();
    let text = new TextDecoder("utf-8").decode(buf);
    if (text.includes("�")) text = new TextDecoder("windows-1252").decode(buf);
    const parsed = Papa.parse<string[]>(text, { skipEmptyLines: false });
    return [{ name: file.name.replace(/\.csv$/i, ""), rows: parsed.data }];
  }
  const XLSX = await import("xlsx");
  const wb = XLSX.read(await file.arrayBuffer(), { type: "array", cellDates: false });
  return wb.SheetNames.map((name) => ({
    name,
    rows: XLSX.utils.sheet_to_json<(string | number | null)[]>(wb.Sheets[name]!, { header: 1, raw: true, defval: null, blankrows: true }),
  }));
}

async function downloadTemplate() {
  const XLSX = await import("xlsx");
  const rows = [
    ["Data", "Descrição", "Categoria", "Valor", "Tipo", "Forma de pagamento", "Parcela", "Observação"],
    ["05/11/2026", "Salário", "Salário", "3.575,00", "Receita", "Transferência", "", ""],
    ["06/11/2026", "Supermercado", "Mercado", "389,90", "Despesa", "Débito", "", ""],
    ["10/11/2026", "Geladeira", "Cartão de crédito", "490,00", "Despesa", "Crédito", "5/12", "Loja do centro"],
    ["12/11/2026", "Reserva", "Reserva de emergência", "300,00", "Investimento", "Pix", "", ""],
  ];
  const ws = XLSX.utils.aoa_to_sheet(rows);
  ws["!cols"] = [{ wch: 12 }, { wch: 22 }, { wch: 22 }, { wch: 12 }, { wch: 14 }, { wch: 20 }, { wch: 9 }, { wch: 24 }];
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, "Lançamentos");
  XLSX.writeFile(wb, "modelo-fynavero.xlsx");
}

type CatChoice = string; // id da categoria ou "new"

export default function ImportPage() {
  const qc = useQueryClient();
  const cats = useCategories();
  const inputRef = useRef<HTMLInputElement>(null);
  const [step, setStep] = useState(0);
  const [file, setFile] = useState<File | null>(null);
  const [sheets, setSheets] = useState<Sheet[]>([]);
  const [plans, setPlans] = useState<SheetPlan[]>([]);
  const [defaultKind, setDefaultKind] = useState<Kind>("DESPESA");
  const [reading, setReading] = useState(false);
  const [readError, setReadError] = useState<string | null>(null);
  const [choices, setChoices] = useState<Record<string, CatChoice>>({});
  const [dupes, setDupes] = useState<Set<string>>(new Set());
  const [includeDupes, setIncludeDupes] = useState(false);
  const [checking, setChecking] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ImportBatch | null>(null);
  const [editing, setEditing] = useState<number | null>(null);

  const rows = useMemo(() => (step >= 2 ? extractRows(sheets, plans, defaultKind, currentMonth()) : []), [step, sheets, plans, defaultKind]);
  const valid = rows.filter((r) => r.errors.length === 0);
  const invalid = rows.filter((r) => r.errors.length > 0);
  const toImport = valid.filter((r) => includeDupes || !dupes.has(r.key));

  const groups = useMemo(() => {
    const m = new Map<string, { label: string; kind: Kind; count: number; cents: number }>();
    for (const r of valid) {
      const k = categoryKey(r);
      const g = m.get(k) ?? { label: r.categoryLabel, kind: r.kind, count: 0, cents: 0 };
      g.count++;
      g.cents += r.amountCents!;
      m.set(k, g);
    }
    return [...m.entries()];
  }, [valid]);

  function reset() {
    setStep(0);
    setFile(null);
    setSheets([]);
    setPlans([]);
    setResult(null);
    setDupes(new Set());
    setChoices({});
    if (inputRef.current) inputRef.current.value = "";
  }

  async function onFile(f: File | undefined) {
    if (!f) return;
    setReadError(null);
    if (!/\.(xlsx|xls|csv)$/i.test(f.name)) return setReadError("Envie um arquivo .xlsx, .xls ou .csv.");
    if (f.size > MAX_BYTES) return setReadError("O arquivo passa de 5 MB. Divida a planilha e tente de novo.");
    setReading(true);
    try {
      const s = await readFile(f);
      const p = analyze(s, Number(currentMonth().slice(0, 4)));
      if (!p.some((x) => x.include)) {
        setReadError("Não encontramos uma linha de cabeçalho com “Valor”. Confira a planilha ou use o modelo.");
        return;
      }
      setFile(f);
      setSheets(s);
      setPlans(p);
      setStep(1);
    } catch {
      setReadError("Não conseguimos ler esse arquivo. Ele pode estar protegido por senha ou corrompido.");
    } finally {
      setReading(false);
    }
  }

  function defaultChoice(label: string, kind: Kind, list: Category[]): CatChoice {
    const f = fold(label);
    const hit = list.find((c) => c.kind === kind && fold(c.name) === f);
    if (hit) return String(hit.id);
    if (label === NO_CATEGORY) {
      const any = list.find((c) => c.kind === kind && !c.hidden);
      if (any) return String(any.id);
    }
    return "new";
  }

  async function goReview() {
    setStep(2);
    setChecking(true);
    try {
      const all = extractRows(sheets, plans, defaultKind, currentMonth()).filter((r) => r.errors.length === 0);
      const init: Record<string, CatChoice> = {};
      for (const r of all) {
        const k = categoryKey(r);
        if (!(k in init)) init[k] = choices[k] ?? defaultChoice(r.categoryLabel, r.kind, cats.data ?? []);
      }
      setChoices(init);
      const res = await api<{ duplicateIndexes: number[] }>("/api/imports/check", {
        method: "POST",
        body: { rows: all.map((r) => ({ date: r.date, amountCents: r.amountCents, description: r.description || null })) },
      });
      setDupes(new Set(res.duplicateIndexes.map((i) => all[i]!.key)));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setChecking(false);
    }
  }

  async function doImport() {
    setImporting(true);
    try {
      const resolved = new Map<string, number>();
      for (const [k, g] of groups) {
        const choice = choices[k];
        if (choice && choice !== "new") {
          resolved.set(k, Number(choice));
          continue;
        }
        const name = g.label === NO_CATEGORY ? `Importado (${KIND_LABEL[g.kind]})` : g.label;
        const existing = (await qc.fetchQuery({ queryKey: ["categories"], queryFn: () => api<Category[]>("/api/categories") }))
          .find((c) => c.kind === g.kind && fold(c.name) === fold(name));
        const c = existing ?? (await api<Category>("/api/categories", { method: "POST", body: { name: name.slice(0, 60), kind: g.kind, group: "Outros" } }));
        resolved.set(k, c.id);
        if (!existing) await qc.invalidateQueries({ queryKey: ["categories"] });
      }
      const batch = await api<ImportBatch>("/api/imports", {
        method: "POST",
        body: {
          fileName: file?.name ?? "planilha",
          rows: toImport.map((r) => ({
            kind: r.kind,
            date: r.date,
            description: r.description || undefined,
            categoryId: resolved.get(categoryKey(r))!,
            amountCents: r.amountCents,
            paymentMethod: r.paymentMethod,
            installmentNumber: r.installmentNumber,
            installmentTotal: r.installmentTotal,
            note: r.note || undefined,
          })),
        },
      });
      await qc.invalidateQueries({ queryKey: ["categories"] });
      invalidateFinance(qc);
      setResult(batch);
      toast.success(`${batch.rowCount} lançamentos importados`);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setImporting(false);
    }
  }

  const sumKind = (k: Kind) => toImport.filter((r) => r.kind === k).reduce((s, r) => s + r.amountCents!, 0);
  const updatePlan = (i: number, patch: Partial<SheetPlan>) => setPlans((ps) => ps.map((p, j) => (j === i ? { ...p, ...patch } : p)));

  return (
    <div className="space-y-4">
      <PageHeader
        title="Importar planilha"
        description="A planilha é lida no seu navegador e descartada. Só os lançamentos confirmados ficam salvos."
      />

      <ol className="grid grid-cols-4 gap-2" aria-label="Etapas da importação">
        {STEPS.map((s, i) => (
          <li key={s} className="space-y-1" aria-current={i === step ? "step" : undefined}>
            <Progress value={i <= step ? 1 : 0} max={1} label={`${s}${i < step ? " concluído" : ""}`} className="h-1.5" />
            <span className={i === step ? "text-sm font-semibold" : "text-sm text-muted-foreground"}>
              {i + 1}. {s}
            </span>
          </li>
        ))}
      </ol>

      {step === 0 && (
        <Card className="space-y-4">
          <label
            htmlFor="import-file"
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              void onFile(e.dataTransfer.files[0]);
            }}
            className="flex min-h-48 cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-input p-6 text-center hover:bg-muted"
          >
            {reading ? <Spinner className="size-8" /> : <Upload className="size-8 text-primary-text" aria-hidden="true" />}
            <span className="font-semibold">Toque para escolher ou arraste o arquivo aqui</span>
            <span className="text-sm text-muted-foreground">.xlsx, .xls ou .csv · até 5 MB</span>
            <input
              id="import-file"
              ref={inputRef}
              type="file"
              accept=".xlsx,.xls,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel,text/csv"
              className="sr-only"
              onChange={(e) => void onFile(e.target.files?.[0])}
            />
          </label>
          {readError && (
            <p role="alert" className="flex items-center gap-2 rounded-lg bg-negative-subtle px-3 py-2 text-sm text-negative">
              <CircleAlert className="size-4 shrink-0" aria-hidden="true" /> {readError}
            </p>
          )}
          <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
            <Button variant="outline" onClick={downloadTemplate}>
              <Download /> Baixar modelo de planilha
            </Button>
            Entendemos aba única, uma aba por mês (ex.: “Nov 26”), cabeçalho fora da linha 1, valores como “1.160,42” e parcelas “5/12”.
          </div>
        </Card>
      )}

      {step === 1 && (
        <Card className="space-y-5">
          <CardTitle>O que encontramos em “{file?.name}”</CardTitle>
          <Field label="Quando a planilha não tem coluna “Tipo”, considerar as linhas como" className="max-w-sm">
            <Select value={defaultKind} onChange={(e) => setDefaultKind(e.target.value as Kind)}>
              <option value="DESPESA">Despesas</option>
              <option value="RECEITA">Receitas</option>
              <option value="INVESTIMENTO">Investimentos</option>
            </Select>
          </Field>
          <ul className="space-y-3">
            {plans.map((p, i) => {
              const cols = (Object.keys(p.mapping) as ColumnField[]).map((f) => FIELD_LABELS[f]);
              const needsMonth = p.include && p.mapping.date === undefined && !p.month;
              return (
                <li key={p.name} className="rounded-lg border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Checkbox checked={p.include} onChange={(v) => updatePlan(i, { include: v && p.headerRow >= 0 })}>
                      <span className="font-semibold">Aba “{p.name}”</span>
                      {p.month && <Badge className="ml-2">mês {p.month.split("-").reverse().join("/")}</Badge>}
                    </Checkbox>
                    {p.headerRow >= 0 ? (
                      <Button variant="ghost" size="sm" onClick={() => setEditing(i)}>Ajustar colunas</Button>
                    ) : (
                      <span className="text-sm text-muted-foreground">Sem cabeçalho reconhecido</span>
                    )}
                  </div>
                  {p.headerRow >= 0 && (
                    <p className="mt-1 text-sm text-muted-foreground">
                      Cabeçalho na linha {p.headerRow + 1} · Colunas: {cols.join(", ") || "nenhuma"}
                    </p>
                  )}
                  {needsMonth && (
                    <Field label="Esta aba não tem data. De qual mês são os lançamentos?" className="mt-3 max-w-xs">
                      <Input type="month" value={p.month ?? ""} onChange={(e) => updatePlan(i, { month: e.target.value || null })} />
                    </Field>
                  )}
                  {p.include && p.topIncomes.length > 0 && (
                    <div className="mt-3 space-y-2 rounded-lg bg-muted p-3">
                      <p className="text-sm font-medium">Receitas escritas no topo da aba</p>
                      {p.topIncomes.map((t, j) => (
                        <Checkbox
                          key={t.row}
                          checked={t.include}
                          onChange={(v) => updatePlan(i, { topIncomes: p.topIncomes.map((x, k) => (k === j ? { ...x, include: v } : x)) })}
                        >
                          {t.label}: <Money cents={t.cents} className="font-semibold" />
                        </Checkbox>
                      ))}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="flex flex-wrap justify-between gap-2">
            <Button variant="ghost" onClick={reset}>Escolher outro arquivo</Button>
            <Button
              onClick={goReview}
              disabled={!plans.some((p) => p.include) || plans.some((p) => p.include && p.mapping.date === undefined && !p.month)}
            >
              Revisar lançamentos
            </Button>
          </div>
        </Card>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Card as="div">
              <p className="text-sm text-muted-foreground">Linhas válidas</p>
              <p className="money text-2xl font-bold">{valid.length}</p>
            </Card>
            <Card as="div">
              <p className="text-sm text-muted-foreground">Com erro (não serão importadas)</p>
              <p className="money text-2xl font-bold">{invalid.length}</p>
            </Card>
            <Card as="div">
              <p className="text-sm text-muted-foreground">Possíveis duplicados</p>
              <p className="money text-2xl font-bold">{checking ? <Spinner /> : dupes.size}</p>
            </Card>
          </div>

          <Card aria-labelledby="map-cat">
            <CardTitle id="map-cat">Para qual categoria vai cada uma?</CardTitle>
            <div className="overflow-x-auto">
              <table className="w-full min-w-max text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th scope="col" className="py-2 font-medium">Na planilha</th>
                    <th scope="col" className="py-2 font-medium">Tipo</th>
                    <th scope="col" className="py-2 text-right font-medium">Linhas</th>
                    <th scope="col" className="py-2 text-right font-medium">Total</th>
                    <th scope="col" className="py-2 pl-4 font-medium">Na Fynavero</th>
                  </tr>
                </thead>
                <tbody>
                  {groups.map(([k, g]) => (
                    <tr key={k} className="border-b last:border-0">
                      <th scope="row" className="py-2 pr-3 text-left font-medium">{g.label}</th>
                      <td className="py-2 pr-3">{KIND_LABEL[g.kind]}</td>
                      <td className="money py-2 text-right">{g.count}</td>
                      <td className="py-2 text-right"><Money cents={g.cents} /></td>
                      <td className="py-2 pl-4">
                        <Select
                          aria-label={`Categoria para ${g.label}`}
                          value={choices[k] ?? "new"}
                          onChange={(e) => setChoices((c) => ({ ...c, [k]: e.target.value }))}
                          className="min-w-56"
                        >
                          <option value="new">
                            Criar categoria “{g.label === NO_CATEGORY ? `Importado (${KIND_LABEL[g.kind]})` : g.label}”
                          </option>
                          {(cats.data ?? []).filter((c) => c.kind === g.kind).map((c) => (
                            <option key={c.id} value={c.id}>{c.name}</option>
                          ))}
                        </Select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          {dupes.size > 0 && (
            <Card aria-labelledby="dup">
              <CardTitle id="dup">Possíveis duplicados</CardTitle>
              <p className="mb-3 text-sm text-muted-foreground">
                {dupes.size} {dupes.size === 1 ? "linha tem" : "linhas têm"} mesma data, valor e descrição de lançamentos que você já tem.
              </p>
              <Checkbox checked={includeDupes} onChange={setIncludeDupes}>Importar os possíveis duplicados mesmo assim</Checkbox>
            </Card>
          )}

          {invalid.length > 0 && (
            <Card aria-labelledby="err">
              <CardTitle id="err">Linhas com erro</CardTitle>
              <ul className="max-h-64 divide-y overflow-y-auto text-sm">
                {invalid.slice(0, 200).map((r) => (
                  <li key={r.key} className="flex flex-wrap gap-x-3 py-2">
                    <span className="font-medium">{r.sheet} · linha {r.line}</span>
                    <span className="text-negative">{r.errors.join(", ")}</span>
                    {r.description && <span className="text-muted-foreground">“{r.description}”</span>}
                  </li>
                ))}
              </ul>
            </Card>
          )}

          <Card aria-labelledby="prev">
            <CardTitle id="prev">Prévia</CardTitle>
            <PreviewTable rows={toImport.slice(0, 50)} dupes={dupes} />
            {toImport.length > 50 && <p className="mt-2 text-xs text-muted-foreground">Mostrando 50 de {toImport.length} linhas.</p>}
          </Card>

          <div className="flex flex-wrap justify-between gap-2">
            <Button variant="ghost" onClick={() => setStep(1)}>Voltar</Button>
            <Button onClick={() => setStep(3)} disabled={toImport.length === 0 || checking}>Continuar</Button>
          </div>
        </div>
      )}

      {step === 3 && (
        <Card className="space-y-5">
          {result ? (
            <div className="space-y-4 text-center">
              <CircleCheck className="mx-auto size-10 text-positive" aria-hidden="true" />
              <h2 className="text-xl font-bold">Importação concluída</h2>
              <p className="text-sm text-muted-foreground">{result.rowCount} lançamentos importados de “{result.fileName}”.</p>
              <SummaryList rec={result.receitas} des={result.despesas} inv={result.investido} />
              <div className="flex flex-wrap justify-center gap-2">
                <Button asChild><Link to="/lancamentos">Ver lançamentos</Link></Button>
                <Button variant="outline" onClick={reset}><FileSpreadsheet /> Importar outra planilha</Button>
              </div>
              <p className="text-xs text-muted-foreground">Mudou de ideia? Desfaça em “Importações anteriores”, logo abaixo.</p>
            </div>
          ) : (
            <>
              <CardTitle>Confira antes de importar</CardTitle>
              <p className="text-sm">
                Vamos importar <strong>{toImport.length}</strong> lançamentos
                {invalid.length > 0 && <> e ignorar {invalid.length} linhas com erro</>}
                {!includeDupes && dupes.size > 0 && <> e {dupes.size} possíveis duplicados</>}.
              </p>
              <SummaryList rec={sumKind("RECEITA")} des={sumKind("DESPESA")} inv={sumKind("INVESTIMENTO")} />
              <div className="flex flex-wrap justify-between gap-2">
                <Button variant="ghost" onClick={() => setStep(2)} disabled={importing}>Voltar</Button>
                <Button size="lg" onClick={doImport} disabled={importing}>
                  {importing && <Spinner />} Importar {toImport.length} lançamentos
                </Button>
              </div>
            </>
          )}
        </Card>
      )}

      <ImportHistory />

      {editing !== null && plans[editing] && (
        <Modal
          open
          onOpenChange={(o) => !o && setEditing(null)}
          title={`Colunas da aba “${plans[editing]!.name}”`}
          description="Escolha qual coluna da planilha corresponde a cada informação."
          footer={<Button onClick={() => setEditing(null)}>Pronto</Button>}
        >
          <div className="space-y-3">
            {(Object.keys(FIELD_LABELS) as ColumnField[]).map((f) => (
              <Field key={f} label={FIELD_LABELS[f] + (f === "amount" ? " (obrigatório)" : "")}>
                <Select
                  value={plans[editing]!.mapping[f] ?? ""}
                  onChange={(e) => {
                    const v = e.target.value === "" ? undefined : Number(e.target.value);
                    const mapping = { ...plans[editing]!.mapping, [f]: v };
                    if (v === undefined) delete mapping[f];
                    updatePlan(editing, { mapping });
                  }}
                >
                  <option value="">Não usar</option>
                  {plans[editing]!.headers.map((h, i) => (
                    <option key={i} value={i}>{h}</option>
                  ))}
                </Select>
              </Field>
            ))}
          </div>
        </Modal>
      )}
    </div>
  );
}

function SummaryList({ rec, des, inv }: { rec: number; des: number; inv: number }) {
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

function PreviewTable({ rows, dupes }: { rows: ParsedRow[]; dupes: Set<string> }) {
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

export function ImportHistory() {
  const qc = useQueryClient();
  const batches = useImports();
  const [confirm, setConfirm] = useState<ImportBatch | null>(null);
  const [busy, setBusy] = useState(false);
  if (!batches.data || batches.data.length === 0) return null;
  return (
    <Card aria-labelledby="hist">
      <CardTitle id="hist">
        <span className="inline-flex items-center gap-2"><History className="size-4" aria-hidden="true" /> Importações anteriores</span>
      </CardTitle>
      <ul className="divide-y">
        {batches.data.map((b) => (
          <li key={b.id} className="flex flex-wrap items-center gap-3 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{b.fileName}</p>
              <p className="text-xs text-muted-foreground">
                {new Date(b.createdAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })} · {b.rowCount} linhas
              </p>
            </div>
            {b.undoneAt ? (
              <Badge>Desfeita</Badge>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirm(b)}>
                <Undo2 /> Desfazer
              </Button>
            )}
          </li>
        ))}
      </ul>
      <Modal
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Desfazer importação?"
        description={confirm ? `Remove os ${confirm.rowCount} lançamentos importados de “${confirm.fileName}”. Os demais não são afetados.` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirm(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={async () => {
                if (!confirm) return;
                setBusy(true);
                try {
                  await api(`/api/imports/${confirm.id}`, { method: "DELETE" });
                  invalidateFinance(qc);
                  toast.success("Importação desfeita");
                  setConfirm(null);
                } catch (e) {
                  toast.error(errorMessage(e));
                } finally {
                  setBusy(false);
                }
              }}
            >
              {busy && <Spinner />} Desfazer importação
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">Essa ação não apaga a planilha do seu computador.</p>
      </Modal>
    </Card>
  );
}
