import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Trash2 } from "lucide-react";
import { CurrencyInput } from "@/components/CurrencyInput";
import { Money } from "@/components/Money";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/feedback";
import { Field, Input } from "@/components/ui/field";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { api, errorMessage } from "@/lib/api";
import { centsToInput, formatDate, parseMoneyInput, todayISO } from "@/lib/format";
import { useTxMutations } from "@/lib/queries";
import type { Tx } from "@/lib/types";

/**
 * O que já foi guardado num investimento ou objetivo: cada valor com a data, editável e excluível,
 * e um atalho para ajustar o total (lança a diferença como um novo valor guardado na data escolhida).
 */
export function SavingsHistoryDialog({
  categoryId,
  name,
  open,
  onClose,
}: {
  categoryId: number;
  name: string;
  open: boolean;
  onClose: () => void;
}) {
  const { openEdit, deleteWithUndo } = useQuickAdd();
  const { create } = useTxMutations();
  const history = useQuery({
    queryKey: ["transactions", "by-category", categoryId],
    queryFn: () => api<Tx[]>(`/api/transactions/by-category/${categoryId}`),
    enabled: open,
  });
  const today = todayISO();
  const saved = (history.data ?? []).filter((t) => t.date <= today).reduce((s, t) => s + t.amountCents, 0);
  const [total, setTotal] = useState<string | null>(null);
  const [date, setDate] = useState(today);
  const [error, setError] = useState<string | null>(null);
  const totalText = total ?? (history.data ? centsToInput(saved) : "");

  async function adjust() {
    const wanted = parseMoneyInput(totalText);
    if (wanted === null || wanted < 0) {
      setError("Informe quanto você tem guardado hoje.");
      return;
    }
    const diff = wanted - saved;
    if (diff === 0) {
      setError("O valor já é esse.");
      return;
    }
    if (diff < 0) {
      setError("Para diminuir, corrija ou exclua na lista abaixo os valores que não estão certos.");
      return;
    }
    setError(null);
    try {
      await create.mutateAsync({
        kind: "INVESTIMENTO",
        amountCents: diff,
        categoryId,
        date,
        description: "Ajuste do valor guardado",
      });
      setTotal(null);
      toast.success("Valor guardado atualizado");
    } catch (e) {
      toast.error(errorMessage(e));
    }
  }

  return (
    <Modal open={open} onOpenChange={(o) => !o && onClose()} title={`Guardado em ${name}`} description="Corrija valores e datas, ou ajuste o total.">
      <div className="space-y-5">
        <section aria-labelledby="ajuste" className="space-y-3 rounded-lg border p-3">
          <h3 id="ajuste" className="text-sm font-semibold">Ajustar o total guardado</h3>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Field label="Quanto você tem guardado" error={error ?? undefined}>
              <CurrencyInput value={totalText} onChange={(v) => setTotal(v)} />
            </Field>
            <Field label="Data em que guardou a diferença">
              <Input type="date" value={date} max={today} onChange={(e) => setDate(e.target.value)} />
            </Field>
          </div>
          <Button onClick={adjust} disabled={create.isPending || !history.data}>
            {create.isPending && <Spinner />} Salvar ajuste
          </Button>
        </section>

        <section aria-labelledby="valores" className="space-y-2">
          <h3 id="valores" className="text-sm font-semibold">Valores guardados</h3>
          {history.isLoading ? (
            <Spinner />
          ) : !history.data?.length ? (
            <p className="text-sm text-muted-foreground">Nada guardado ainda. Use “Investir” ou “Guardar dinheiro” para lançar o primeiro valor.</p>
          ) : (
            <ul className="divide-y rounded-lg border">
              {history.data.map((t) => (
                <li key={t.id} className="flex items-center gap-2 px-3 py-2">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{t.description || "Valor guardado"}</p>
                    <p className="flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                      {formatDate(t.date)}
                      {t.status === "PREVISTO" && <Badge tone="forecast">Previsto</Badge>}
                      {t.recurrenceId && <span>· todo mês</span>}
                    </p>
                  </div>
                  <Money cents={t.amountCents} className="text-sm font-semibold" />
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Editar valor de ${formatDate(t.date)}`}
                    onClick={() => {
                      onClose();
                      openEdit(t);
                    }}
                  >
                    <Pencil />
                  </Button>
                  <Button variant="ghost" size="icon" aria-label={`Excluir valor de ${formatDate(t.date)}`} onClick={() => void deleteWithUndo(t)}>
                    <Trash2 />
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </Modal>
  );
}
