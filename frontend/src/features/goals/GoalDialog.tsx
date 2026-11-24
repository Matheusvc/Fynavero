import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { CurrencyInput } from "@/components/CurrencyInput";
import { IconPicker } from "@/components/IconPicker";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/feedback";
import { Field, Input } from "@/components/ui/field";
import { errorMessage } from "@/lib/api";
import { centsToInput, parseMoneyInput, todayISO } from "@/lib/format";
import { useGoalMutations } from "@/lib/queries";
import type { Goal } from "@/lib/types";

/** Criar ou editar um objetivo: nome, quanto juntar, até quando e quanto já está guardado. */
export function GoalDialog({ goal, open, onClose }: { goal?: Goal | null; open: boolean; onClose: () => void }) {
  const editing = !!goal;
  const m = useGoalMutations();
  const [name, setName] = useState(goal?.name ?? "");
  const [target, setTarget] = useState(goal ? centsToInput(goal.targetCents) : "");
  const [deadline, setDeadline] = useState(goal?.deadline ?? "");
  const [icon, setIcon] = useState(goal?.icon ?? "target");
  const [initial, setInitial] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const busy = m.create.isPending || m.update.isPending;

  async function submit(e: FormEvent) {
    e.preventDefault();
    const targetCents = parseMoneyInput(target);
    const initialCents = initial ? parseMoneyInput(initial) : 0;
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = "Dê um nome, como Viagem ou Reserva de emergência.";
    if (!targetCents || targetCents <= 0) errs.target = "Informe quanto quer juntar.";
    if (initialCents === null) errs.initial = "Valor inválido.";
    if (deadline && deadline < todayISO()) errs.deadline = "O prazo precisa ser hoje ou depois.";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const body = { name: name.trim(), targetCents: targetCents!, deadline: deadline || null, icon, initialCents: initialCents || null };
    try {
      if (goal) await m.update.mutateAsync({ id: goal.id, body });
      else await m.create.mutateAsync(body);
      toast.success(editing ? "Objetivo atualizado" : "Objetivo criado");
      onClose();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={editing ? "Editar objetivo" : "Novo objetivo"}
      description="Guardar dinheiro para ele é lançar um investimento nesta categoria."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" form="goal-form" disabled={busy}>
            {busy && <Spinner />} {editing ? "Salvar" : "Criar objetivo"}
          </Button>
        </>
      }
    >
      <form id="goal-form" onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Nome do objetivo" error={errors.name}>
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={60} placeholder="Ex.: Viagem para o Nordeste" />
        </Field>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Quanto quer juntar" error={errors.target}>
            <CurrencyInput value={target} onChange={setTarget} />
          </Field>
          <Field label="Até quando (opcional)" error={errors.deadline} hint="Com prazo, mostramos quanto guardar por mês.">
            <Input type="date" min={todayISO()} value={deadline} onChange={(e) => setDeadline(e.target.value)} />
          </Field>
        </div>
        {!editing && (
          <Field label="Já tenho guardado (opcional)" error={errors.initial}>
            <CurrencyInput value={initial} onChange={setInitial} />
          </Field>
        )}
        <IconPicker value={icon} onChange={setIcon} />
      </form>
    </Modal>
  );
}
