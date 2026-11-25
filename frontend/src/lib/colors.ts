import { useMe, useTags } from "./queries";
import type { ChartColors } from "./types";

/** Paleta que a pessoa escolhe para categorias, etiquetas e linhas dos gráficos (tokens --chart-1 a --chart-12). */
export const PALETTE = [
  { token: "chart-1", name: "Laranja" },
  { token: "chart-2", name: "Grafite" },
  { token: "chart-3", name: "Pêssego" },
  { token: "chart-4", name: "Cinza" },
  { token: "chart-5", name: "Ferrugem" },
  { token: "chart-6", name: "Prata" },
  { token: "chart-7", name: "Azul" },
  { token: "chart-8", name: "Verde" },
  { token: "chart-9", name: "Roxo" },
  { token: "chart-10", name: "Turquesa" },
  { token: "chart-11", name: "Âmbar" },
  { token: "chart-12", name: "Rosa" },
] as const;

/** `var(--chart-7)` a partir do token. */
export const cssColor = (token: string) => `var(--${token})`;

export const colorName = (token?: string | null) => PALETTE.find((c) => c.token === token)?.name;

/** Linhas dos gráficos e a cor padrão de cada uma. */
export const SERIES = [
  { key: "income", label: "Receitas", fallback: "series-income" },
  { key: "expense", label: "Despesas", fallback: "series-expense" },
  { key: "invest", label: "Investido", fallback: "series-invest" },
] as const;

export type SeriesKey = (typeof SERIES)[number]["key"];

/** Token de cor de cada linha dos gráficos: a escolhida pela pessoa ou a padrão. */
export function useSeriesColors(): Record<SeriesKey, string> {
  const chosen: ChartColors | undefined = useMe().data?.chartColors;
  return Object.fromEntries(SERIES.map((s) => [s.key, chosen?.[s.key] || s.fallback])) as Record<SeriesKey, string>;
}

/** Cor escolhida para cada etiqueta (sem cor = neutra). */
export function useTagColors(): (tag: string) => string | undefined {
  const tags = useTags().data;
  const map = new Map(tags?.map((t) => [t.tag, t.color ?? undefined]));
  return (tag) => map.get(tag);
}
