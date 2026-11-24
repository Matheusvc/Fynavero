import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowDown, ArrowUp, Eye, EyeOff, Pencil, Plus } from "lucide-react";
import { Money } from "@/components/Money";
import { CurrencyInput } from "@/components/CurrencyInput";
import { IconPicker } from "@/components/IconPicker";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select } from "@/components/ui/field";
import { Badge } from "@/components/ui/badge";
import { Segmented } from "@/components/ui/segmented";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { centsToInput, parseMoneyInput } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { EXPENSE_GROUPS, KIND_LABEL, KIND_PLURAL } from "@/lib/labels";
import { invalidateFinance, useCategories } from "@/lib/queries";
import type { Category, Kind } from "@/lib/types";
import { Section, useSave } from "../shared";

type CatDraft = { id?: number; name: string; kind: Kind; group: string; icon: string; goal: string };

export function CategoriesSection() {
  const qc = useQueryClient();
  const cats = useCategories();
  const [kind, setKind] = useState<Kind>("DESPESA");
  const [draft, setDraft] = useState<CatDraft | null>(null);
  const s = useSave();
  const list = (cats.data ?? []).filter((c) => c.kind === kind).sort((a, b) => a.sortOrder - b.sortOrder);

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["categories"] });
    invalidateFinance(qc);
  };

  async function move(i: number, dir: -1 | 1) {
    const j = i + dir;
    if (j < 0 || j >= list.length) return;
    const ids = list.map((c) => c.id);
    [ids[i], ids[j]] = [ids[j]!, ids[i]!];
    qc.setQueryData<Category[]>(["categories"], (old) =>
      old?.map((c) => (c.kind === kind ? { ...c, sortOrder: ids.indexOf(c.id) } : c)),
    );
    try {
      await api("/api/categories/order", { method: "PUT", body: { kind, ids } });
    } catch (e) {
      toast.error(errorMessage(e));
    }
    refresh();
  }

  async function toggleHidden(c: Category) {
    await s.run(
      () => api(`/api/categories/${c.id}`, { method: "PUT", body: { name: c.name, hidden: !c.hidden } }),
      c.hidden ? `“${c.name}” voltou a aparecer` : `“${c.name}” foi ocultada`,
    );
    refresh();
  }

  async function save() {
    if (!draft) return;
    const goalCents = draft.goal ? parseMoneyInput(draft.goal) ?? 0 : 0;
    const body = { name: draft.name.trim(), kind: draft.kind, group: draft.group, icon: draft.icon, goalCents };
    const ok = await s.run(
      () => (draft.id ? api(`/api/categories/${draft.id}`, { method: "PUT", body }) : api("/api/categories", { method: "POST", body })),
      draft.id ? "Categoria atualizada" : "Categoria criada",
    );
    if (ok) {
      setDraft(null);
      refresh();
    }
  }

  return (
    <Section
      id="categorias"
      title="Categorias"
      description="Crie, renomeie, oculte, reordene e troque o ícone. Categorias ocultas somem do lançamento rápido, mas o histórico continua."
      action={
        <Button variant="outline" onClick={() => setDraft({ name: "", kind, group: kind === "DESPESA" ? "Outros" : "", icon: "tag", goal: "" })}>
          <Plus /> Nova categoria
        </Button>
      }
    >
      <Segmented
        label="Tipo de categoria"
        value={kind}
        onChange={setKind}
        className="mb-3"
        options={(["DESPESA", "RECEITA", "INVESTIMENTO"] as Kind[]).map((k) => ({ value: k, label: KIND_PLURAL[k] }))}
      />
      <ul className="divide-y rounded-lg border">
        {list.map((c, i) => (
          <li key={c.id} className="flex items-center gap-2 px-2 py-2 sm:px-3">
            <CategoryIcon icon={c.icon} color={c.color} size="sm" />
            <div className="min-w-0 flex-1">
              <p className={c.hidden ? "truncate font-medium text-muted-foreground line-through" : "truncate font-medium"}>{c.name}</p>
              <p className="text-xs text-muted-foreground">
                {c.kind === "DESPESA" ? c.group : KIND_LABEL[c.kind]}
                {c.goalCents ? <> · {c.kind === "DESPESA" ? "limite" : "meta"} <Money cents={c.goalCents} />{c.kind === "DESPESA" && "/mês"}</> : null}
                {c.hidden && <> · <Badge>Oculta</Badge></>}
              </p>
            </div>
            <Button variant="ghost" size="icon" onClick={() => move(i, -1)} disabled={i === 0} aria-label={`Subir ${c.name}`}><ArrowUp /></Button>
            <Button variant="ghost" size="icon" onClick={() => move(i, 1)} disabled={i === list.length - 1} aria-label={`Descer ${c.name}`}><ArrowDown /></Button>
            <Button variant="ghost" size="icon" onClick={() => toggleHidden(c)} aria-label={c.hidden ? `Mostrar ${c.name}` : `Ocultar ${c.name}`}>
              {c.hidden ? <Eye /> : <EyeOff />}
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label={`Editar ${c.name}`}
              onClick={() => setDraft({ id: c.id, name: c.name, kind: c.kind, group: c.group, icon: c.icon, goal: c.goalCents ? centsToInput(c.goalCents) : "" })}
            >
              <Pencil />
            </Button>
          </li>
        ))}
        {list.length === 0 && <li className="p-4 text-sm text-muted-foreground">Nenhuma categoria deste tipo.</li>}
      </ul>

      <Modal
        open={!!draft}
        onOpenChange={(o) => !o && setDraft(null)}
        title={draft?.id ? "Editar categoria" : `Nova categoria de ${draft ? KIND_LABEL[draft.kind].toLowerCase() : ""}`}
        footer={<Button onClick={save} disabled={s.busy || !draft?.name.trim()}>{s.busy && <Spinner />} {draft?.id ? "Salvar alterações" : "Criar categoria"}</Button>}
      >
        {draft && (
          <div className="space-y-4">
            <Field label="Nome">
              <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={60} autoFocus />
            </Field>
            {draft.kind === "DESPESA" && (
              <Field label="Grupo">
                <Select value={draft.group} onChange={(e) => setDraft({ ...draft, group: e.target.value })}>
                  {EXPENSE_GROUPS.map((g) => <option key={g}>{g}</option>)}
                </Select>
              </Field>
            )}
            {draft.kind === "DESPESA" && (
              <Field label="Limite por mês (opcional)" hint="Avisamos quando os gastos desta categoria chegarem a 80% e a 100% do limite.">
                <CurrencyInput value={draft.goal} onChange={(goal) => setDraft({ ...draft, goal })} />
              </Field>
            )}
            {draft.kind === "INVESTIMENTO" && (
              <Field label="Meta (opcional)" hint="Para dar prazo e acompanhar o progresso, use a tela Objetivos.">
                <CurrencyInput value={draft.goal} onChange={(goal) => setDraft({ ...draft, goal })} />
              </Field>
            )}
            <IconPicker value={draft.icon} onChange={(icon) => setDraft({ ...draft, icon })} />
          </div>
        )}
      </Modal>
    </Section>
  );
}
