import { toast } from "sonner";
import { api } from "@/lib/api";
import { formatBRL } from "@/lib/format";
import type { Budget } from "@/lib/types";

/** Depois de salvar uma despesa, avisa se a categoria passou de 80% (ou de 100%) do limite do mês. */
export async function warnLimit(categoryId: number, date: string) {
  try {
    const budgets = await api<Budget[]>(`/api/budgets?month=${date.slice(0, 7)}`);
    const b = budgets.find((x) => x.categoryId === categoryId);
    if (!b || b.level === "OK") return;
    const detail = `${formatBRL(b.spentCents)} de ${formatBRL(b.limitCents)} neste mês`;
    if (b.level === "ESTOURADO") toast.error(`${b.name} passou do limite (${Math.floor(b.pctBps / 100)}%)`, { description: detail });
    else toast.warning(`${b.name}: ${Math.floor(b.pctBps / 100)}% do limite`, { description: detail });
  } catch {
    // O aviso é um extra: se falhar, o lançamento já foi salvo e o sino mostra o alerta depois.
  }
}
