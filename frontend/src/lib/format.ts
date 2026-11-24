const ZONE = "America/Sao_Paulo";
const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const plain = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const pct = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

/** Valores sempre em centavos inteiros. Negativos usam o sinal de menos tipográfico (−). */
export function formatBRL(centavos: number, opts: { signed?: boolean } = {}): string {
  const abs = brl.format(Math.abs(centavos) / 100).replace(/ /g, " ");
  if (centavos < 0) return `−${abs}`;
  if (opts.signed && centavos > 0) return `+${abs}`;
  return abs;
}

/** Texto para leitor de tela, com o sinal por extenso. */
export function speakBRL(centavos: number, opts: { signed?: boolean } = {}): string {
  const abs = brl.format(Math.abs(centavos) / 100);
  if (centavos < 0) return `menos ${abs}`;
  if (opts.signed && centavos > 0) return `mais ${abs}`;
  return abs;
}

/** "1.234,56" a partir de centavos (para campos de edição). */
export function centsToInput(centavos: number): string {
  return plain.format(centavos / 100);
}

/** Converte o que a pessoa digitou ("1.234,56", "1234,5", "R$ 10") em centavos. Nulo se inválido. */
export function parseMoneyInput(text: string): number | null {
  const t = text.replace(/[R$\s]/g, "");
  if (!t) return null;
  let normalized: string;
  if (t.includes(",")) normalized = t.replace(/\./g, "").replace(",", ".");
  else if (/^\d{1,3}(\.\d{3})+$/.test(t)) normalized = t.replace(/\./g, "");
  else normalized = t;
  if (!/^\d+(\.\d{0,2})?$/.test(normalized)) return null;
  const [int, dec = ""] = normalized.split(".");
  return Number(int) * 100 + Number((dec + "00").slice(0, 2));
}

export function formatPct(bps: number): string {
  return `${pct.format(bps / 100)}%`;
}

export function todayISO(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: ZONE }).format(new Date());
}

export function currentMonth(): string {
  return todayISO().slice(0, 7);
}

function parts(iso: string): [number, number, number] {
  const [y, m, d] = iso.split("-").map(Number);
  return [y!, m!, d ?? 1];
}

/** dd/mm/aaaa */
export function formatDate(iso: string): string {
  const [y, m, d] = parts(iso);
  return `${String(d).padStart(2, "0")}/${String(m).padStart(2, "0")}/${y}`;
}

const weekday = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });
const shortDay = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "short", timeZone: "UTC" });

export function formatDayHeader(iso: string): string {
  const [y, m, d] = parts(iso);
  const s = weekday.format(new Date(Date.UTC(y, m - 1, d)));
  const today = todayISO();
  if (iso === today) return `Hoje, ${s.split(", ")[1] ?? s}`;
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function formatShortDay(iso: string): string {
  const [y, m, d] = parts(iso);
  return shortDay.format(new Date(Date.UTC(y, m - 1, d))).replace(".", "");
}

const monthFmt = new Intl.DateTimeFormat("pt-BR", { month: "long", year: "numeric", timeZone: "UTC" });
const monthOnly = new Intl.DateTimeFormat("pt-BR", { month: "long", timeZone: "UTC" });

/** "2026-11" → "Novembro 2026" */
export function monthLabel(ym: string): string {
  const [y, m] = parts(ym);
  const s = monthFmt.format(new Date(Date.UTC(y, m - 1, 1))).replace(" de ", " ");
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function monthName(ym: string): string {
  const [y, m] = parts(ym);
  return monthOnly.format(new Date(Date.UTC(y, m - 1, 1)));
}

export function shiftMonth(ym: string, delta: number): string {
  const [y, m] = parts(ym);
  const d = new Date(Date.UTC(y, m - 1 + delta, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** Primeiro dia do mês selecionado, ou hoje se for o mês atual. */
export function defaultDateFor(ym: string): string {
  const today = todayISO();
  return today.startsWith(ym) ? today : `${ym}-01`;
}

/** Variação em pontos-base (10000 = 100%). Nulo quando não há base de comparação. */
export function changeBps(current: number, previous: number): number | null {
  if (previous === 0) return null;
  return Math.round(((current - previous) * 10000) / Math.abs(previous));
}

/** Soma n meses a uma data ISO, preservando o dia (limitado ao último dia do mês de destino). */
export function addMonthsISO(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const target = new Date(Date.UTC(y!, m! - 1 + n, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d!, last));
  return target.toISOString().slice(0, 10);
}

/** Quantidade de meses entre dois "AAAA-MM" (pode ser negativa). */
export function monthsBetween(fromYm: string, toYm: string): number {
  const [ay, am] = fromYm.split("-").map(Number);
  const [by, bm] = toYm.split("-").map(Number);
  return (by! - ay!) * 12 + (bm! - am!);
}
