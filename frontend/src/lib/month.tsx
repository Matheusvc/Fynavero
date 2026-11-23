import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { currentMonth, shiftMonth } from "./format";

type MonthCtx = { month: string; setMonth: (m: string) => void; prev: () => void; next: () => void };

const Ctx = createContext<MonthCtx | null>(null);
const KEY = "selected-month";

function initial(): string {
  try {
    const v = sessionStorage.getItem(KEY);
    if (v && /^\d{4}-\d{2}$/.test(v)) return v;
  } catch {
    // sem armazenamento: começa no mês atual
  }
  return currentMonth();
}

export function MonthProvider({ children }: { children: ReactNode }) {
  const [month, setState] = useState(initial);
  const setMonth = useCallback((m: string) => {
    setState(m);
    try {
      sessionStorage.setItem(KEY, m);
    } catch {
      // ignora
    }
  }, []);
  const prev = useCallback(() => setMonth(shiftMonth(month, -1)), [month, setMonth]);
  const next = useCallback(() => setMonth(shiftMonth(month, 1)), [month, setMonth]);
  return <Ctx.Provider value={{ month, setMonth, prev, next }}>{children}</Ctx.Provider>;
}

export function useMonth() {
  const c = useContext(Ctx);
  if (!c) throw new Error("useMonth precisa de MonthProvider");
  return c;
}
