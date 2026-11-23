import type { Kind, PaymentMethod } from "../types";

/** Leitura de planilhas em português. Funções puras: recebem linhas e devolvem lançamentos em centavos. */

export type Cell = string | number | boolean | Date | null | undefined;
export type Sheet = { name: string; rows: Cell[][] };
export type ColumnField = "date" | "description" | "category" | "amount" | "kind" | "payment" | "installment" | "note";
export type Mapping = Partial<Record<ColumnField, number>>;

export type TopIncome = { label: string; cents: number; row: number; include: boolean };

export type SheetPlan = {
  name: string;
  include: boolean;
  headerRow: number; // índice (0) da linha de cabeçalho; -1 se não encontrado
  mapping: Mapping;
  month: string | null; // AAAA-MM quando a aba representa um mês
  topIncomes: TopIncome[];
  headers: string[];
};

export type ParsedRow = {
  key: string;
  sheet: string;
  line: number; // linha como aparece no Excel (1-based)
  date: string | null;
  description: string;
  categoryLabel: string;
  kind: Kind;
  amountCents: number | null;
  paymentMethod: PaymentMethod | null;
  installmentNumber: number | null;
  installmentTotal: number | null;
  note: string;
  errors: string[];
  fromTop?: boolean;
};

export const FIELD_LABELS: Record<ColumnField, string> = {
  date: "Data",
  description: "Descrição",
  category: "Categoria",
  amount: "Valor",
  kind: "Tipo",
  payment: "Forma de pagamento",
  installment: "Parcela",
  note: "Observação",
};

export const NO_CATEGORY = "(sem categoria)";

export function fold(v: unknown): string {
  return String(v ?? "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/ /g, " ")
    .trim();
}

const SYNONYMS: [ColumnField, string[]][] = [
  ["date", ["data", "dia", "vencimento", "data da compra", "data do lancamento", "dt"]],
  ["amount", ["valor", "valor r$", "quantia", "preco", "total", "montante", "r$", "custo", "valor total"]],
  ["installment", ["parcela", "parcelas", "n parcela", "no parcela", "parc"]],
  ["payment", ["forma de pagamento", "forma pagamento", "meio de pagamento", "pagamento", "forma", "cartao"]],
  ["category", ["categoria", "classificacao", "grupo", "tipo de gasto", "tipo de despesa", "centro de custo"]],
  ["description", ["descricao", "historico", "item", "lancamento", "detalhe", "detalhes", "estabelecimento", "nome", "o que"]],
  ["note", ["observacao", "observacoes", "obs", "nota", "comentario", "comentarios"]],
  ["kind", ["tipo", "natureza", "movimento", "entrada/saida", "entrada saida", "e/s"]],
];

function headerField(cell: Cell): ColumnField | null {
  if (typeof cell !== "string") return null;
  const n = fold(cell).replace(/[^a-z0-9/ ]/g, " ").replace(/\s+/g, " ").trim();
  if (!n || n.length > 40) return null;
  for (const [field, syns] of SYNONYMS) {
    if (syns.some((s) => n === s || n.startsWith(`${s} `))) return field;
  }
  return null;
}

function mappingFor(row: Cell[]): Mapping {
  const m: Mapping = {};
  row.forEach((cell, i) => {
    const f = headerField(cell);
    if (f && m[f] === undefined) m[f] = i;
  });
  return m;
}

/** Procura a linha de cabeçalho nas primeiras 30 linhas (precisa ter "Valor" e mais uma coluna conhecida). */
export function detectHeader(rows: Cell[][]): { row: number; mapping: Mapping } | null {
  let best: { row: number; mapping: Mapping; score: number } | null = null;
  for (let r = 0; r < Math.min(rows.length, 30); r++) {
    const mapping = mappingFor(rows[r] ?? []);
    const score = Object.keys(mapping).length;
    if (mapping.amount === undefined || score < 2) continue;
    if (!best || score > best.score) best = { row: r, mapping, score };
  }
  return best ? { row: best.row, mapping: best.mapping } : null;
}

const MONTHS = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

/** "Nov 26", "novembro/2026", "11-2026", "2026-11" → "2026-11". */
export function parseSheetMonth(name: string, fallbackYear: number): string | null {
  const n = fold(name);
  const pad = (m: number) => String(m).padStart(2, "0");
  const fullYear = (y: string) => (y.length === 2 ? 2000 + Number(y) : Number(y));
  let m = n.match(/(\d{4})[-/._](\d{1,2})\b/);
  if (m && Number(m[2]) >= 1 && Number(m[2]) <= 12) return `${m[1]}-${pad(Number(m[2]))}`;
  m = n.match(/\b(\d{1,2})[-/._](\d{4}|\d{2})\b/);
  if (m && Number(m[1]) >= 1 && Number(m[1]) <= 12) return `${fullYear(m[2]!)}-${pad(Number(m[1]))}`;
  m = n.match(/\b(jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)[a-z]*\.?(?:[\s/_\-.]*(?:de\s*)?(\d{4}|\d{2}))?\b/);
  if (m) {
    const month = MONTHS.indexOf(m[1]!) + 1;
    const year = m[2] ? fullYear(m[2]) : fallbackYear;
    return `${year}-${pad(month)}`;
  }
  return null;
}

/** Valor em centavos com sinal. Aceita "1.160,42", "R$ -10,00", "(25,90)", "1160.42" e números do Excel. */
export function parseMoney(cell: Cell): number | null {
  if (typeof cell === "number") return Number.isFinite(cell) ? Math.round(cell * 100) : null;
  if (typeof cell !== "string") return null;
  let t = cell.replace(/ /g, " ").trim();
  if (!t) return null;
  let negative = false;
  if (/^\(.*\)$/.test(t)) {
    negative = true;
    t = t.slice(1, -1);
  }
  t = t.replace(/r\$/gi, "").replace(/\s/g, "");
  if (/^[-−–]/.test(t)) {
    negative = true;
    t = t.slice(1);
  } else if (/[-−–]$/.test(t)) {
    negative = true;
    t = t.slice(0, -1);
  }
  if (!/^[\d.,]+$/.test(t)) return null;
  const lastComma = t.lastIndexOf(",");
  const lastDot = t.lastIndexOf(".");
  let intPart: string;
  let decPart = "";
  if (lastComma >= 0 && lastDot >= 0) {
    const decSep = lastComma > lastDot ? "," : ".";
    const thouSep = decSep === "," ? "." : ",";
    const clean = t.split(thouSep).join("");
    [intPart, decPart = ""] = clean.split(decSep) as [string, string?];
  } else if (lastComma >= 0) {
    if (/^\d{1,3}(,\d{3})+$/.test(t)) intPart = t.replace(/,/g, "");
    else [intPart, decPart = ""] = t.split(",") as [string, string?];
  } else if (lastDot >= 0) {
    if (/^\d{1,3}(\.\d{3})+$/.test(t)) intPart = t.replace(/\./g, "");
    else [intPart, decPart = ""] = t.split(".") as [string, string?];
  } else {
    intPart = t;
  }
  if (!/^\d+$/.test(intPart || "0") || !/^\d*$/.test(decPart)) return null;
  let cents = Number(intPart || "0") * 100;
  if (decPart.length <= 2) cents += Number((decPart + "00").slice(0, 2));
  else cents += Math.round(Number(`0.${decPart}`) * 100);
  return negative ? -cents : cents;
}

function iso(y: number, m: number, d: number): string | null {
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return null;
  return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

/** Data em AAAA-MM-DD. Usa o mês da aba para dias soltos ("15") ou "15/11" sem ano. */
export function parseDate(cell: Cell, month: string | null): string | null {
  const [my, mm] = month ? month.split("-").map(Number) : [undefined, undefined];
  if (cell instanceof Date) return iso(cell.getUTCFullYear(), cell.getUTCMonth() + 1, cell.getUTCDate());
  if (typeof cell === "number") {
    if (cell >= 1 && cell <= 31 && Number.isInteger(cell) && my && mm) return iso(my, mm, cell);
    if (cell > 20000 && cell < 80000) {
      const dt = new Date(Date.UTC(1899, 11, 30) + Math.round(cell) * 86400000);
      return iso(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
    }
    return null;
  }
  if (typeof cell !== "string") return null;
  const t = cell.trim();
  let m = t.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (m) return iso(Number(m[1]), Number(m[2]), Number(m[3]));
  m = t.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$/);
  if (m) {
    const y = m[3]!.length === 2 ? 2000 + Number(m[3]) : Number(m[3]);
    return iso(y, Number(m[2]), Number(m[1]));
  }
  m = t.match(/^(\d{1,2})[/.-](\d{1,2})$/);
  if (m) return iso(my ?? new Date().getFullYear(), Number(m[2]), Number(m[1]));
  m = t.match(/^(\d{1,2})$/);
  if (m && my && mm) return iso(my, mm, Number(m[1]));
  return null;
}

export function parseInstallment(cell: Cell): { n: number; total: number } | null {
  const m = String(cell ?? "").match(/(\d{1,3})\s*(?:\/|de)\s*(\d{1,3})/i);
  if (!m) return null;
  const n = Number(m[1]);
  const total = Number(m[2]);
  return n >= 1 && total >= n && total <= 240 ? { n, total } : null;
}

export function parseKind(cell: Cell): Kind | null {
  const t = fold(cell);
  if (!t) return null;
  if (/receita|entrada|credito|ganho|renda|salario|recebimento/.test(t)) return "RECEITA";
  if (/invest|aporte|aplicacao|reserva/.test(t)) return "INVESTIMENTO";
  if (/despesa|saida|gasto|debito|pagamento|conta/.test(t)) return "DESPESA";
  return null;
}

export function parsePayment(cell: Cell): PaymentMethod | null {
  const t = fold(cell);
  if (!t) return null;
  if (/\bpix\b/.test(t)) return "PIX";
  if (/dinheiro|especie/.test(t)) return "DINHEIRO";
  if (/vale.?transporte|\bvt\b/.test(t)) return "VT";
  if (/\bva\b|\bvr\b|vale.?(refeicao|alimentacao)|alimentacao|refeicao/.test(t)) return "VA_VR";
  if (/debito/.test(t)) return "DEBITO";
  if (/credito|cartao/.test(t)) return "CREDITO";
  if (/boleto/.test(t)) return "BOLETO";
  if (/transfer|\bted\b|\bdoc\b/.test(t)) return "TRANSFERENCIA";
  return null;
}

const TOP_SKIP = /total|saldo|sobra|resumo|gasto|despesa|planilha|\bmes\b|data|\bano\b|meta|limite|orcamento|media/;

function findTopIncomes(rows: Cell[][], headerRow: number): TopIncome[] {
  const out: TopIncome[] = [];
  for (let r = 0; r < headerRow; r++) {
    const row = rows[r] ?? [];
    const li = row.findIndex((c) => typeof c === "string" && /[a-zà-ú]/i.test(c) && parseMoney(c) === null);
    if (li < 0) continue;
    const label = String(row[li]).trim().replace(/:$/, "");
    if (TOP_SKIP.test(fold(label))) continue;
    for (let j = li + 1; j < row.length; j++) {
      const cents = parseMoney(row[j]);
      if (cents !== null && cents > 0) {
        out.push({ label, cents, row: r, include: true });
        break;
      }
    }
  }
  return out;
}

/** Primeira leitura: encontra cabeçalho, colunas, mês da aba e receitas no topo. */
export function analyze(sheets: Sheet[], fallbackYear: number): SheetPlan[] {
  return sheets.map((s) => {
    const h = detectHeader(s.rows);
    const headerRow = h?.row ?? -1;
    return {
      name: s.name,
      include: !!h,
      headerRow,
      mapping: h?.mapping ?? {},
      month: parseSheetMonth(s.name, fallbackYear),
      topIncomes: h ? findTopIncomes(s.rows, headerRow) : [],
      headers: headerRow >= 0 ? (s.rows[headerRow] ?? []).map((c, i) => (c == null || c === "" ? `Coluna ${i + 1}` : String(c))) : [],
    };
  });
}

const TOTAL_ROW = /^(total|subtotal|saldo|soma|sobra)\b/;

function text(cell: Cell): string {
  if (cell == null) return "";
  return String(cell).trim();
}

/** Segunda leitura: transforma as linhas em lançamentos, marcando os erros com o motivo. */
export function extractRows(sheets: Sheet[], plans: SheetPlan[], defaultKind: Kind, fallbackMonth: string): ParsedRow[] {
  const out: ParsedRow[] = [];
  sheets.forEach((s, si) => {
    const p = plans[si];
    if (!p || !p.include || p.headerRow < 0 || p.mapping.amount === undefined) return;
    const map = p.mapping;
    const cell = (row: Cell[], f: ColumnField) => (map[f] === undefined ? undefined : row[map[f]!]);

    for (const t of p.topIncomes) {
      if (!t.include) continue;
      out.push({
        key: `${s.name}:top:${t.row}`,
        sheet: s.name,
        line: t.row + 1,
        date: `${p.month ?? fallbackMonth}-01`,
        description: t.label,
        categoryLabel: t.label,
        kind: "RECEITA",
        amountCents: t.cents,
        paymentMethod: null,
        installmentNumber: null,
        installmentTotal: null,
        note: "",
        errors: [],
        fromTop: true,
      });
    }

    const body = s.rows.slice(p.headerRow + 1);
    const signedSheet = map.kind === undefined && body.some((r) => (parseMoney(cell(r, "amount")) ?? 0) < 0);

    body.forEach((row, i) => {
      if (!row || row.every((c) => c == null || text(c) === "")) return;
      const description = text(cell(row, "description"));
      const categoryRaw = text(cell(row, "category"));
      const amountCell = cell(row, "amount");
      if (TOTAL_ROW.test(fold(description)) || TOTAL_ROW.test(fold(categoryRaw)) || headerField(amountCell) === "amount") return;
      const errors: string[] = [];

      const signedCents = parseMoney(amountCell);
      if (signedCents === null) errors.push(text(amountCell) ? "Valor inválido" : "Valor vazio");
      else if (signedCents === 0) errors.push("Valor zerado");

      let date: string | null;
      if (map.date !== undefined) {
        date = parseDate(cell(row, "date"), p.month);
        if (!date) errors.push(text(cell(row, "date")) ? "Data inválida" : "Data vazia");
      } else if (p.month) {
        date = `${p.month}-01`;
      } else {
        date = null;
        errors.push("Sem data e a aba não indica o mês");
      }

      let kind: Kind = defaultKind;
      if (map.kind !== undefined) kind = parseKind(cell(row, "kind")) ?? defaultKind;
      else if (signedSheet && signedCents !== null) kind = signedCents < 0 ? "DESPESA" : "RECEITA";

      const inst = parseInstallment(cell(row, "installment"));
      out.push({
        key: `${s.name}:${p.headerRow + 2 + i}`,
        sheet: s.name,
        line: p.headerRow + 2 + i,
        date,
        description,
        categoryLabel: categoryRaw || NO_CATEGORY,
        kind,
        amountCents: signedCents === null ? null : Math.abs(signedCents),
        paymentMethod: parsePayment(cell(row, "payment")),
        installmentNumber: inst?.n ?? null,
        installmentTotal: inst?.total ?? null,
        note: text(cell(row, "note")),
        errors,
      });
    });
  });
  return out;
}

/** Chave para mapear categorias da planilha para as da pessoa. */
export function categoryKey(r: Pick<ParsedRow, "kind" | "categoryLabel">): string {
  return `${r.kind}|${fold(r.categoryLabel)}`;
}
