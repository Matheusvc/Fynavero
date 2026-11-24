import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Money } from "@/components/Money";
import { CurrencyInput } from "@/components/CurrencyInput";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Switch } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import { centsToInput, parseMoneyInput } from "@/lib/format";
import { invalidateFinance, useIncomeSources } from "@/lib/queries";
import type { IncomeSource } from "@/lib/types";
import { ConfirmModal, Section, useSave } from "../shared";

type IncomeDraft = { id?: number; name: string; amount: string; fixed: boolean; day: string };

export function IncomeSection() {
  const qc = useQueryClient();
  const incomes = useIncomeSources();
  const [draft, setDraft] = useState<IncomeDraft | null>(null);
  const [remove, setRemove] = useState<IncomeSource | null>(null);
  const s = useSave();

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["categories"] });
    invalidateFinance(qc);
  };

  async function save() {
    if (!draft) return;
    const body = { name: draft.name.trim(), amountCents: parseMoneyInput(draft.amount) ?? 0, fixed: draft.fixed, day: Number(draft.day) || 5 };
    const ok = await s.run(
      () => (draft.id ? api(`/api/income-sources/${draft.id}`, { method: "PUT", body }) : api("/api/income-sources", { method: "POST", body })),
      draft.id ? "Fonte de renda atualizada" : "Fonte de renda criada",
    );
    if (ok) {
      setDraft(null);
      refresh();
    }
  }

  return (
    <Section
      id="renda"
      title="Fontes de renda"
      description="Renda fixa entra todo mês automaticamente como Previsto."
      action={<Button variant="outline" onClick={() => setDraft({ name: "", amount: "", fixed: true, day: "5" })}><Plus /> Nova fonte</Button>}
    >
      <ul className="divide-y rounded-lg border">
        {(incomes.data ?? []).map((i) => (
          <li key={i.id} className="flex items-center gap-3 px-3 py-2">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{i.name}</p>
              <p className="text-xs text-muted-foreground">{i.fixed ? "Fixa" : "Variável (estimativa)"}{i.day ? ` · todo dia ${i.day}` : ""}</p>
            </div>
            <Money cents={i.amountCents} className="font-semibold" />
            <Button variant="ghost" size="icon" aria-label={`Editar ${i.name}`} onClick={() => setDraft({ id: i.id, name: i.name, amount: centsToInput(i.amountCents), fixed: i.fixed, day: String(i.day ?? 5) })}><Pencil /></Button>
            <Button variant="ghost" size="icon" aria-label={`Remover ${i.name}`} onClick={() => setRemove(i)}><Trash2 /></Button>
          </li>
        ))}
        {incomes.data?.length === 0 && <li className="p-4 text-sm text-muted-foreground">Nenhuma fonte de renda cadastrada.</li>}
      </ul>

      <Modal
        open={!!draft}
        onOpenChange={(o) => !o && setDraft(null)}
        title={draft?.id ? "Editar fonte de renda" : "Nova fonte de renda"}
        footer={<Button onClick={save} disabled={s.busy || !draft?.name.trim()}>{s.busy && <Spinner />} Salvar fonte de renda</Button>}
      >
        {draft && (
          <div className="space-y-4">
            <Field label="Nome"><Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={60} /></Field>
            <Field label="Valor por mês"><CurrencyInput value={draft.amount} onChange={(amount) => setDraft({ ...draft, amount })} /></Field>
            <Switch
              label="Renda fixa"
              description={draft.fixed ? "Mesmo valor todo mês" : "Valor muda: entra como estimativa, você ajusta quando receber"}
              checked={draft.fixed}
              onChange={(fixed) => setDraft({ ...draft, fixed })}
            />
            <Field label={draft.fixed ? "Dia em que recebe" : "Dia em que costuma receber"}>
              <Input type="number" inputMode="numeric" min={1} max={31} value={draft.day} onChange={(e) => setDraft({ ...draft, day: e.target.value })} />
            </Field>
          </div>
        )}
      </Modal>
      <ConfirmModal
        open={!!remove}
        onClose={() => setRemove(null)}
        title={`Remover “${remove?.name}”?`}
        body="Os recebimentos futuros previstos desta renda serão removidos. O histórico continua."
        confirm="Remover fonte de renda"
        busy={s.busy}
        onConfirm={async () => {
          if (await s.run(() => api(`/api/income-sources/${remove!.id}`, { method: "DELETE" }), "Fonte de renda removida")) {
            setRemove(null);
            refresh();
          }
        }}
      />
    </Section>
  );
}
