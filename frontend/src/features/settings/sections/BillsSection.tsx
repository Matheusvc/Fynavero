import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { Money } from "@/components/Money";
import { CurrencyInput } from "@/components/CurrencyInput";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/field";
import { Segmented } from "@/components/ui/segmented";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import { centsToInput, parseMoneyInput } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { KIND_LABEL, PAYMENT_METHODS } from "@/lib/labels";
import { invalidateFinance, useCategories, useRecurrences } from "@/lib/queries";
import type { Kind, PaymentMethod, Recurrence } from "@/lib/types";
import { ConfirmModal, Section, useSave } from "../shared";

type BillDraft = { id?: number; kind: Kind; description: string; amount: string; day: string; categoryId: string; payment: PaymentMethod | "" };

export function BillsSection() {
  const qc = useQueryClient();
  const recs = useRecurrences();
  const cats = useCategories();
  const [draft, setDraft] = useState<BillDraft | null>(null);
  const [end, setEnd] = useState<Recurrence | null>(null);
  const s = useSave();
  const catById = new Map((cats.data ?? []).map((c) => [c.id, c]));
  const expenseCats = (cats.data ?? []).filter((c) => c.kind === (draft?.kind ?? "DESPESA") && !c.hidden);

  async function save() {
    if (!draft) return;
    const body = {
      kind: draft.kind,
      categoryId: Number(draft.categoryId),
      description: draft.description.trim() || null,
      amountCents: parseMoneyInput(draft.amount) ?? 0,
      day: Math.min(31, Math.max(1, Number(draft.day) || 1)),
      paymentMethod: draft.payment || null,
    };
    const ok = await s.run(
      () => (draft.id ? api(`/api/recurrences/${draft.id}`, { method: "PUT", body }) : api("/api/recurrences", { method: "POST", body })),
      draft.id ? "Conta fixa atualizada" : "Conta fixa criada",
    );
    if (ok) {
      setDraft(null);
      invalidateFinance(qc);
    }
  }

  return (
    <Section
      id="contas-fixas"
      title="Contas fixas e recorrentes"
      description="Entram todo mês como Previsto e aparecem em “Próximos vencimentos”."
      action={
        <Button
          variant="outline"
          onClick={() => setDraft({ kind: "DESPESA", description: "", amount: "", day: "10", categoryId: String(expenseCats[0]?.id ?? ""), payment: "" })}
        >
          <Plus /> Nova conta fixa
        </Button>
      }
    >
      <ul className="divide-y rounded-lg border">
        {(recs.data ?? []).map((r) => {
          const c = catById.get(r.categoryId);
          return (
            <li key={r.id} className="flex items-center gap-3 px-3 py-2">
              <CategoryIcon icon={c?.icon ?? "repeat"} color={c?.color} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{r.description || c?.name}</p>
                <p className="text-xs text-muted-foreground">{KIND_LABEL[r.kind]} · todo dia {r.day}</p>
              </div>
              <Money cents={r.kind === "RECEITA" ? r.amountCents : -r.amountCents} tone={r.kind === "RECEITA" ? "positive" : "none"} className="font-semibold" />
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Editar ${r.description || c?.name}`}
                onClick={() => setDraft({ id: r.id, kind: r.kind, description: r.description ?? "", amount: centsToInput(r.amountCents), day: String(r.day), categoryId: String(r.categoryId), payment: r.paymentMethod ?? "" })}
              >
                <Pencil />
              </Button>
              <Button variant="ghost" size="icon" aria-label={`Encerrar ${r.description || c?.name}`} onClick={() => setEnd(r)}><Trash2 /></Button>
            </li>
          );
        })}
        {recs.data?.length === 0 && <li className="p-4 text-sm text-muted-foreground">Nenhuma conta fixa.</li>}
      </ul>

      <Modal
        open={!!draft}
        onOpenChange={(o) => !o && setDraft(null)}
        title={draft?.id ? "Editar conta fixa" : "Nova conta fixa"}
        description={draft?.id ? "As alterações valem a partir de hoje." : undefined}
        footer={<Button onClick={save} disabled={s.busy || !draft?.categoryId || !parseMoneyInput(draft?.amount ?? "")}>{s.busy && <Spinner />} Salvar conta fixa</Button>}
      >
        {draft && (
          <div className="space-y-4">
            {!draft.id && (
              <Segmented
                label="Tipo"
                value={draft.kind}
                onChange={(k) => setDraft({ ...draft, kind: k, categoryId: String((cats.data ?? []).find((c) => c.kind === k && !c.hidden)?.id ?? "") })}
                options={[{ value: "DESPESA" as Kind, label: "Despesa" }, { value: "RECEITA" as Kind, label: "Receita" }]}
              />
            )}
            <Field label="Nome"><Input value={draft.description} onChange={(e) => setDraft({ ...draft, description: e.target.value })} maxLength={120} placeholder="Ex.: Aluguel" /></Field>
            <Field label="Valor"><CurrencyInput value={draft.amount} onChange={(amount) => setDraft({ ...draft, amount })} /></Field>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Dia do vencimento">
                <Input type="number" inputMode="numeric" min={1} max={31} value={draft.day} onChange={(e) => setDraft({ ...draft, day: e.target.value })} />
              </Field>
              <Field label="Categoria">
                <Select value={draft.categoryId} onChange={(e) => setDraft({ ...draft, categoryId: e.target.value })}>
                  {expenseCats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </Select>
              </Field>
            </div>
            <Field label="Forma de pagamento">
              <Select value={draft.payment} onChange={(e) => setDraft({ ...draft, payment: e.target.value as PaymentMethod | "" })}>
                <option value="">Não informar</option>
                {PAYMENT_METHODS.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
              </Select>
            </Field>
          </div>
        )}
      </Modal>
      <ConfirmModal
        open={!!end}
        onClose={() => setEnd(null)}
        title="Encerrar conta fixa?"
        body="Os lançamentos previstos a partir de amanhã serão removidos. O que já passou continua no histórico."
        confirm="Encerrar conta fixa"
        busy={s.busy}
        onConfirm={async () => {
          if (await s.run(() => api(`/api/recurrences/${end!.id}`, { method: "DELETE" }), "Conta fixa encerrada")) {
            setEnd(null);
            invalidateFinance(qc);
          }
        }}
      />
    </Section>
  );
}
