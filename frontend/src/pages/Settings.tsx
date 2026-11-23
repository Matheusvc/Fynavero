import { useState, type ReactNode } from "react";
import { Link } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowDown,
  ArrowUp,
  CalendarClock,
  ChartColumn,
  ChevronRight,
  Download,
  Eye,
  EyeOff,
  FileSpreadsheet,
  LogOut,
  Pencil,
  Plus,
  Settings as SettingsIcon,
  Trash2,
} from "lucide-react";
import { Money } from "@/components/Money";
import { CurrencyInput } from "@/components/CurrencyInput";
import { IconPicker } from "@/components/IconPicker";
import { ThemeToggle, useLogout } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select, Switch } from "@/components/ui/field";
import { Badge, Card, PageHeader, Segmented, Spinner } from "@/components/ui/misc";
import { ImportHistory } from "./Import";
import { api, errorMessage } from "@/lib/api";
import { centsToInput, parseMoneyInput } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { EXPENSE_GROUPS, KIND_LABEL, KIND_PLURAL, OBJECTIVES, PAYMENT_METHODS } from "@/lib/labels";
import { invalidateFinance, useCategories, useIncomeSources, useMe, useRecurrences, useSetMe } from "@/lib/queries";
import type { Category, IncomeSource, Kind, Me, Objective, PaymentMethod, Recurrence } from "@/lib/types";
import { useTheme, type ThemePreference } from "@/theme/theme";

function useSave() {
  const [busy, setBusy] = useState(false);
  return {
    busy,
    run: async (fn: () => Promise<unknown>, ok: string) => {
      setBusy(true);
      try {
        await fn();
        toast.success(ok);
        return true;
      } catch (e) {
        toast.error(errorMessage(e));
        return false;
      } finally {
        setBusy(false);
      }
    },
  };
}

function Section({ id, title, description, children, action }: {
  id: string;
  title: string;
  description?: ReactNode;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Card aria-labelledby={id} className="scroll-mt-20" >
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id={id} className="text-lg font-semibold">{title}</h2>
          {description && <p className="text-sm text-muted-foreground">{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </Card>
  );
}

// ------------------------------------------------------------------ Perfil

function ProfileSection({ me }: { me: Me }) {
  const setMe = useSetMe();
  const [name, setName] = useState(me.name ?? "");
  const [objective, setObjective] = useState<Objective | "">(me.objective ?? "");
  const s = useSave();
  return (
    <Section id="perfil" title="Perfil">
      <form
        className="grid grid-cols-1 gap-4 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          void s.run(async () => setMe(await api<Me>("/api/profile", { method: "PUT", body: { name, objective: objective || null } })), "Perfil salvo");
        }}
      >
        <Field label="Nome">
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required />
        </Field>
        <Field label="E-mail">
          <Input value={me.email} readOnly disabled />
        </Field>
        <Field label="Objetivo principal" hint="Personaliza os textos e as dicas do Início.">
          <Select value={objective} onChange={(e) => setObjective(e.target.value as Objective)}>
            <option value="">Não informar</option>
            {OBJECTIVES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
        </Field>
        <div className="flex items-end">
          <Button type="submit" disabled={s.busy || !name.trim()}>{s.busy && <Spinner />} Salvar perfil</Button>
        </div>
      </form>
    </Section>
  );
}

// ------------------------------------------------------------------ Categorias

type CatDraft = { id?: number; name: string; kind: Kind; group: string; icon: string; goal: string };

function CategoriesSection() {
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
                {c.goalCents ? <> · meta <Money cents={c.goalCents} /></> : null}
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
            {draft.kind === "INVESTIMENTO" && (
              <Field label="Meta (opcional)">
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

// ------------------------------------------------------------------ Fontes de renda

type IncomeDraft = { id?: number; name: string; amount: string; fixed: boolean; day: string };

function IncomeSection() {
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
              <p className="text-xs text-muted-foreground">{i.fixed ? `Fixa · todo dia ${i.day ?? 5}` : "Variável"}</p>
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
            <Switch label="Renda fixa" description="Mesmo valor todo mês" checked={draft.fixed} onChange={(fixed) => setDraft({ ...draft, fixed })} />
            {draft.fixed && (
              <Field label="Dia em que recebe">
                <Input type="number" inputMode="numeric" min={1} max={31} value={draft.day} onChange={(e) => setDraft({ ...draft, day: e.target.value })} />
              </Field>
            )}
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

// ------------------------------------------------------------------ Contas fixas

type BillDraft = { id?: number; kind: Kind; description: string; amount: string; day: string; categoryId: string; payment: PaymentMethod | "" };

function BillsSection() {
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

// ------------------------------------------------------------------ Contribuição

function ContributionSection({ me }: { me: Me }) {
  const setMe = useSetMe();
  const qc = useQueryClient();
  const cats = useCategories();
  const [enabled, setEnabled] = useState(me.contribution.enabled);
  const [pct, setPct] = useState(String(me.contribution.bps / 100).replace(".", ","));
  const [categoryId, setCategoryId] = useState<string>(me.contribution.categoryId ? String(me.contribution.categoryId) : "");
  const s = useSave();
  const expense = (cats.data ?? []).filter((c) => c.kind === "DESPESA");
  const value = Number(pct.replace(",", "."));
  const valid = value > 0 && value <= 100;
  return (
    <Section id="contribuicao" title="Contribuição (dízimo ou doação)" description="Um percentual sobre o total de receitas do mês, lançado automaticamente como despesa.">
      <div className="space-y-4">
        <Switch label="Calcular contribuição automaticamente" checked={enabled} onChange={setEnabled} />
        {enabled && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Percentual" hint="Sugestão: 10%" error={valid ? null : "Use um valor entre 0,01 e 100."}>
              <Input inputMode="decimal" value={pct} onChange={(e) => setPct(e.target.value)} />
            </Field>
            <Field label="Categoria">
              <Select value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">Contribuição (sem categoria)</option>
                {expense.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
            </Field>
          </div>
        )}
        <Button
          disabled={s.busy || (enabled && !valid)}
          onClick={() =>
            void s.run(async () => {
              setMe(await api<Me>("/api/profile/contribution", {
                method: "PUT",
                body: { enabled, bps: valid ? Math.round(value * 100) : me.contribution.bps, categoryId: categoryId ? Number(categoryId) : null },
              }));
              invalidateFinance(qc);
            }, "Contribuição salva")
          }
        >
          {s.busy && <Spinner />} Salvar contribuição
        </Button>
      </div>
    </Section>
  );
}

// ------------------------------------------------------------------ Aparência, segurança e dados

function AppearanceSection() {
  const { preference, setPreference } = useTheme();
  return (
    <Section id="aparencia" title="Aparência">
      <Segmented<ThemePreference>
        label="Tema"
        value={preference}
        onChange={setPreference}
        className="max-w-sm"
        options={[{ value: "light", label: "Claro" }, { value: "dark", label: "Escuro" }, { value: "system", label: "Sistema" }]}
      />
    </Section>
  );
}

function SecuritySection({ me }: { me: Me }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const s = useSave();
  return (
    <Section id="seguranca" title={me.hasPassword ? "Trocar senha" : "Criar senha"}>
      <form
        className="grid grid-cols-1 gap-4 sm:grid-cols-2"
        onSubmit={async (e) => {
          e.preventDefault();
          if (await s.run(() => api("/api/profile/password", { method: "PUT", body: { current: current || null, next } }), "Senha atualizada")) {
            setCurrent("");
            setNext("");
          }
        }}
      >
        {me.hasPassword && (
          <Field label="Senha atual">
            <Input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
          </Field>
        )}
        <Field label="Nova senha" hint="Pelo menos 8 caracteres.">
          <Input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" minLength={8} required />
        </Field>
        <div>
          <Button type="submit" disabled={s.busy || next.length < 8}>{s.busy && <Spinner />} Salvar senha</Button>
        </div>
      </form>
    </Section>
  );
}

function PrivacySection({ me }: { me: Me }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState("");
  const [password, setPassword] = useState("");
  const s = useSave();
  return (
    <Section id="privacidade" title="Privacidade e dados" description="Seus direitos pela LGPD.">
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" asChild>
          <a href="/api/profile/export" download>
            <Download /> Exportar meus dados
          </a>
        </Button>
        <Button variant="ghost" asChild><Link to="/termos">Termos de uso</Link></Button>
        <Button variant="ghost" asChild><Link to="/privacidade">Política de privacidade</Link></Button>
      </div>
      <div className="mt-6 rounded-lg border border-negative p-4">
        <h3 className="font-semibold text-negative">Excluir conta</h3>
        <p className="mt-1 text-sm text-muted-foreground">Apaga definitivamente todos os seus lançamentos, categorias e configurações. Não dá para desfazer.</p>
        <Button variant="destructive" className="mt-3" onClick={() => setOpen(true)}>
          <Trash2 /> Excluir minha conta
        </Button>
      </div>
      <Modal
        open={open}
        onOpenChange={setOpen}
        title="Excluir conta definitivamente?"
        description="Todos os seus dados serão apagados. Esta ação não pode ser desfeita."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={s.busy || confirm.trim().toUpperCase() !== "EXCLUIR" || (me.hasPassword && !password)}
              onClick={async () => {
                if (await s.run(() => api("/api/profile", { method: "DELETE", body: { confirm, password: password || null } }), "Conta excluída")) {
                  qc.clear();
                  qc.setQueryData(["me"], null);
                  window.location.assign("/entrar");
                }
              }}
            >
              {s.busy && <Spinner />} Excluir definitivamente
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Digite EXCLUIR para confirmar">
            <Input value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" />
          </Field>
          {me.hasPassword && (
            <Field label="Sua senha">
              <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
            </Field>
          )}
        </div>
      </Modal>
    </Section>
  );
}

function ConfirmModal({ open, onClose, title, body, confirm, onConfirm, busy }: {
  open: boolean;
  onClose: () => void;
  title: string;
  body: string;
  confirm: string;
  onConfirm: () => void;
  busy: boolean;
}) {
  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={title}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button variant="destructive" onClick={onConfirm} disabled={busy}>{busy && <Spinner />} {confirm}</Button>
        </>
      }
    >
      <p className="text-sm text-muted-foreground">{body}</p>
    </Modal>
  );
}

const SECTIONS = [
  ["perfil", "Perfil"],
  ["categorias", "Categorias"],
  ["renda", "Fontes de renda"],
  ["contas-fixas", "Contas fixas"],
  ["contribuicao", "Contribuição"],
  ["importar", "Importar planilha"],
  ["aparencia", "Aparência"],
  ["seguranca", "Senha"],
  ["privacidade", "Privacidade e dados"],
] as const;

export default function SettingsPage() {
  const me = useMe();
  const logout = useLogout();
  if (!me.data) return <Spinner />;
  return (
    <div>
      <PageHeader title="Configurações" actions={<Button variant="ghost" onClick={logout}><LogOut /> Sair</Button>} />
      <nav aria-label="Seções" className="mb-4 flex gap-2 overflow-x-auto pb-1">
        {SECTIONS.map(([id, label]) => (
          <a key={id} href={`#${id}`} className="inline-flex min-h-9 shrink-0 items-center rounded-full border bg-card px-3 text-sm hover:bg-muted">
            {label}
          </a>
        ))}
      </nav>
      <div className="space-y-4">
        <ProfileSection me={me.data} />
        <CategoriesSection />
        <IncomeSection />
        <BillsSection />
        <ContributionSection me={me.data} />
        <Section id="importar" title="Importar planilha" description="Traga lançamentos de uma planilha do Excel ou .csv. Cada importação pode ser desfeita.">
          <Button asChild><Link to="/importar"><FileSpreadsheet /> Importar planilha</Link></Button>
        </Section>
        <ImportHistory />
        <AppearanceSection />
        <SecuritySection me={me.data} />
        <PrivacySection me={me.data} />
      </div>
    </div>
  );
}

export function MorePage() {
  const logout = useLogout();
  const links = [
    { to: "/parcelas", label: "Parcelas e Dívidas", icon: CalendarClock },
    { to: "/relatorios", label: "Relatórios", icon: ChartColumn },
    { to: "/importar", label: "Importar planilha", icon: FileSpreadsheet },
    { to: "/configuracoes", label: "Configurações", icon: SettingsIcon },
  ];
  return (
    <div>
      <PageHeader title="Mais" />
      <Card className="p-0 sm:p-0">
        <ul className="divide-y">
          {links.map((l) => (
            <li key={l.to}>
              <Link to={l.to} className="flex min-h-14 items-center gap-3 px-4 hover:bg-muted">
                <l.icon className="size-5 text-primary-text" aria-hidden="true" />
                <span className="flex-1 font-medium">{l.label}</span>
                <ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
              </Link>
            </li>
          ))}
          <li className="flex min-h-14 items-center gap-3 px-4">
            <span className="flex-1 font-medium">Tema</span>
            <ThemeToggle />
          </li>
          <li>
            <button type="button" onClick={logout} className="flex min-h-14 w-full items-center gap-3 px-4 text-left hover:bg-muted">
              <LogOut className="size-5 text-negative" aria-hidden="true" />
              <span className="flex-1 font-medium">Sair</span>
            </button>
          </li>
        </ul>
      </Card>
    </div>
  );
}
