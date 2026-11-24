import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, FileSpreadsheet, Plus, Rocket, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Logo } from "@/components/Logo";
import { CurrencyInput } from "@/components/CurrencyInput";
import { IconPicker } from "@/components/IconPicker";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input, Select, Switch } from "@/components/ui/field";
import { Progress, Spinner } from "@/components/ui/misc";
import { api, errorMessage } from "@/lib/api";
import { parseMoneyInput } from "@/lib/format";
import { CategoryIcon, Icon } from "@/lib/icons";
import { EXPENSE_GROUPS, OBJECTIVES } from "@/lib/labels";
import { useCatalog, useMe, useSetMe } from "@/lib/queries";
import type { Me, Objective } from "@/lib/types";
import { cn } from "@/lib/utils";

type Income = { name: string; amount: string; fixed: boolean; day: string };
type Custom = { name: string; icon: string; group: string };
type Bill = { name: string; amount: string; day: string; categoryKey: string };

const STEPS = 5;

export default function OnboardingPage() {
  const me = useMe();
  const catalog = useCatalog();
  const setMe = useSetMe();
  const qc = useQueryClient();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [name, setName] = useState(me.data?.name ?? "");
  const [objectives, setObjectives] = useState<Objective[]>([]);
  const [incomes, setIncomes] = useState<Income[]>([{ name: "Salário", amount: "", fixed: true, day: "5" }]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [customs, setCustoms] = useState<Custom[]>([]);
  const [contribOn, setContribOn] = useState(false);
  const [contribPct, setContribPct] = useState("10");
  const [bills, setBills] = useState<Bill[]>([]);
  const [customOpen, setCustomOpen] = useState(false);
  const [draft, setDraft] = useState<Custom>({ name: "", icon: "tag", group: "Outros" });
  const [busy, setBusy] = useState(false);

  const groups = useMemo(() => {
    const m = new Map<string, NonNullable<typeof catalog.data>>();
    for (const c of catalog.data ?? []) m.set(c.group, [...(m.get(c.group) ?? []), c]);
    return [...m.entries()];
  }, [catalog.data]);

  const contributionChosen = (catalog.data ?? []).some((c) => c.group === "Contribuições" && selected.has(c.id));

  const chosenKeys = [
    ...(catalog.data ?? []).filter((c) => selected.has(c.id)).map((c) => ({ key: `catalog:${c.id}`, name: c.name })),
    ...customs.map((c) => ({ key: `custom:${c.name}`, name: c.name })),
  ];

  function toggle(id: number) {
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  async function finish(next: "import" | "home") {
    setBusy(true);
    try {
      const pct = Number(contribPct.replace(",", "."));
      const result = await api<Me>("/api/onboarding", {
        method: "POST",
        body: {
          name: name.trim() || undefined,
          objectives,
          incomes: incomes
            .filter((i) => i.name.trim() && parseMoneyInput(i.amount))
            .map((i) => ({ name: i.name.trim(), amountCents: parseMoneyInput(i.amount)!, fixed: i.fixed, day: Number(i.day) || 5 })),
          catalogIds: [...selected],
          customCategories: customs,
          fixedBills: bills
            .filter((b) => b.name.trim() && parseMoneyInput(b.amount))
            .map((b) => ({
              name: b.name.trim(),
              amountCents: parseMoneyInput(b.amount)!,
              day: Math.min(31, Math.max(1, Number(b.day) || 10)),
              categoryKey: b.categoryKey || chosenKeys[0]?.key || "",
            })),
          contribution:
            contributionChosen && contribOn && pct > 0 && pct <= 100
              ? { enabled: true, bps: Math.round(pct * 100) }
              : null,
        },
      });
      setMe(result);
      await qc.invalidateQueries();
      toast.success("Tudo pronto! Sua conta está do seu jeito.");
      navigate(next === "import" ? "/importar" : "/", { replace: true });
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const next = () => setStep((s) => Math.min(STEPS - 1, s + 1));
  const back = () => setStep((s) => Math.max(0, s - 1));

  return (
    <div className="min-h-dvh bg-background">
      <header className="sticky top-0 z-10 border-b bg-background">
        <div className="mx-auto flex max-w-2xl items-center justify-between gap-3 px-4 py-3">
          {step > 0 ? (
            <Button variant="ghost" size="icon" onClick={back} aria-label="Voltar">
              <ArrowLeft />
            </Button>
          ) : (
            <Logo className="text-lg" />
          )}
          <p className="text-sm text-muted-foreground" aria-live="polite">
            Passo {step + 1} de {STEPS}
          </p>
          <Button
            variant="ghost"
            onClick={() => (step === STEPS - 1 ? finish("home") : next())}
            disabled={busy}
          >
            Pular
          </Button>
        </div>
        <Progress value={step + 1} max={STEPS} label="Progresso da configuração" className="h-1 rounded-none" />
      </header>

      <main className="mx-auto max-w-2xl px-4 py-6 pb-32">
        {step === 0 && (
          <section className="space-y-6" aria-labelledby="s0">
            <div>
              <h1 id="s0" className="text-2xl font-bold">Vamos começar!</h1>
              <p className="text-muted-foreground">Como você quer ser chamado e quais são seus objetivos?</p>
            </div>
            <Field label="Seu nome">
              <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} autoComplete="given-name" />
            </Field>
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">
                Seus objetivos <span className="font-normal text-muted-foreground">· pode escolher mais de um</span>
              </legend>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {OBJECTIVES.map((o) => {
                  const on = objectives.includes(o.value);
                  return (
                    <button
                      key={o.value}
                      type="button"
                      aria-pressed={on}
                      onClick={() =>
                        setObjectives((l) => (on ? l.filter((x) => x !== o.value) : [...l, o.value]))
                      }
                      className={cn(
                        "relative flex min-h-20 items-center gap-3 rounded-xl border p-4 text-left transition-colors",
                        on ? "border-primary bg-accent ring-2 ring-primary" : "bg-card hover:bg-muted",
                      )}
                    >
                      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-muted">
                        <Icon name={o.icon} className="size-5" />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-semibold">{o.label}</span>
                        <span className="block text-sm text-muted-foreground">{o.hint}</span>
                      </span>
                      {on && <Check className="size-5 shrink-0 text-primary-text" aria-hidden="true" />}
                    </button>
                  );
                })}
              </div>
            </fieldset>
          </section>
        )}

        {step === 1 && (
          <section className="space-y-5" aria-labelledby="s1">
            <div>
              <h1 id="s1" className="text-2xl font-bold">De onde vem seu dinheiro?</h1>
              <p className="text-muted-foreground">
                Cadastre suas fontes de renda. Elas entram todo mês como Previsto no dia informado; na renda variável o valor é uma estimativa que você ajusta quando receber.
              </p>
            </div>
            {incomes.map((inc, i) => (
              <div key={i} className="space-y-3 rounded-xl border bg-card p-4">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <Field label="Nome">
                    <Input
                      value={inc.name}
                      onChange={(e) => setIncomes((l) => l.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))}
                      maxLength={60}
                    />
                  </Field>
                  <Field label="Valor por mês">
                    <CurrencyInput
                      value={inc.amount}
                      onChange={(v) => setIncomes((l) => l.map((x, j) => (j === i ? { ...x, amount: v } : x)))}
                    />
                  </Field>
                </div>
                <div className="grid grid-cols-1 items-end gap-3 sm:grid-cols-2">
                  <Field label="Tipo">
                    <Select
                      value={inc.fixed ? "fixa" : "variavel"}
                      onChange={(e) => setIncomes((l) => l.map((x, j) => (j === i ? { ...x, fixed: e.target.value === "fixa" } : x)))}
                    >
                      <option value="fixa">Fixa (todo mês o mesmo valor)</option>
                      <option value="variavel">Variável (muda de mês a mês)</option>
                    </Select>
                  </Field>
                  <Field label={inc.fixed ? "Dia em que recebe" : "Dia em que costuma receber"}>
                    <Input
                      type="number"
                      inputMode="numeric"
                      min={1}
                      max={31}
                      value={inc.day}
                      onChange={(e) => setIncomes((l) => l.map((x, j) => (j === i ? { ...x, day: e.target.value } : x)))}
                    />
                  </Field>
                </div>
                {incomes.length > 1 && (
                  <Button variant="ghost" size="sm" className="text-negative" onClick={() => setIncomes((l) => l.filter((_, j) => j !== i))}>
                    <Trash2 /> Remover
                  </Button>
                )}
              </div>
            ))}
            <Button variant="outline" onClick={() => setIncomes((l) => [...l, { name: "", amount: "", fixed: false, day: "5" }])}>
              <Plus /> Adicionar outra renda
            </Button>
          </section>
        )}

        {step === 2 && (
          <section className="space-y-6" aria-labelledby="s2">
            <div>
              <h1 id="s2" className="text-2xl font-bold">Com o que você gasta?</h1>
              <p className="text-muted-foreground">Toque para escolher. Dá para mudar tudo depois.</p>
            </div>
            {catalog.isLoading && <Spinner />}
            {groups.map(([group, items]) => (
              <div key={group}>
                <h2 className="mb-2 text-sm font-semibold text-muted-foreground">{group}</h2>
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  {items.map((c) => {
                    const on = selected.has(c.id);
                    return (
                      <button
                        key={c.id}
                        type="button"
                        aria-pressed={on}
                        onClick={() => toggle(c.id)}
                        className={cn(
                          "relative flex min-h-16 items-center gap-2 rounded-xl border p-3 text-left text-sm font-medium transition-colors",
                          on ? "border-primary bg-accent ring-2 ring-primary" : "bg-card hover:bg-muted",
                        )}
                      >
                        <CategoryIcon icon={c.icon} size="sm" />
                        <span className="min-w-0 flex-1 leading-tight">{c.name}</span>
                        {on && <Check className="size-4 shrink-0 text-primary-text" aria-hidden="true" />}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
            {customs.length > 0 && (
              <div>
                <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Minhas categorias</h2>
                <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                  {customs.map((c, i) => (
                    <li key={c.name} className="flex min-h-16 items-center gap-2 rounded-xl border border-primary bg-accent p-3 text-sm font-medium">
                      <CategoryIcon icon={c.icon} size="sm" />
                      <span className="min-w-0 flex-1 leading-tight">{c.name}</span>
                      <button
                        type="button"
                        className="grid size-9 place-items-center rounded-lg hover:bg-muted"
                        aria-label={`Remover ${c.name}`}
                        onClick={() => setCustoms((l) => l.filter((_, j) => j !== i))}
                      >
                        <Trash2 className="size-4" aria-hidden="true" />
                      </button>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <Button variant="outline" onClick={() => { setDraft({ name: "", icon: "tag", group: "Outros" }); setCustomOpen(true); }}>
              <Plus /> Criar minha categoria
            </Button>

            {contributionChosen && (
              <div className="space-y-3 rounded-xl border bg-card p-4">
                <Switch
                  label="Calcular minha contribuição automaticamente"
                  description="Um percentual das suas receitas do mês entra como despesa (ex.: dízimo de 10%)."
                  checked={contribOn}
                  onChange={setContribOn}
                />
                {contribOn && (
                  <Field label="Percentual sobre as receitas" hint="Sugestão: 10%">
                    <Input inputMode="decimal" value={contribPct} onChange={(e) => setContribPct(e.target.value)} className="max-w-32" />
                  </Field>
                )}
              </div>
            )}
          </section>
        )}

        {step === 3 && (
          <section className="space-y-5" aria-labelledby="s3">
            <div>
              <h1 id="s3" className="text-2xl font-bold">Tem contas fixas?</h1>
              <p className="text-muted-foreground">Aluguel, internet, escola… Elas entram todo mês como Previsto e aparecem em “Próximos vencimentos”. É opcional.</p>
            </div>
            {bills.map((b, i) => (
              <div key={i} className="grid grid-cols-1 gap-3 rounded-xl border bg-card p-4 sm:grid-cols-2">
                <Field label="Nome">
                  <Input value={b.name} onChange={(e) => setBills((l) => l.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} maxLength={60} placeholder="Ex.: Aluguel" />
                </Field>
                <Field label="Valor">
                  <CurrencyInput value={b.amount} onChange={(v) => setBills((l) => l.map((x, j) => (j === i ? { ...x, amount: v } : x)))} />
                </Field>
                <Field label="Dia do vencimento">
                  <Input type="number" inputMode="numeric" min={1} max={31} value={b.day} onChange={(e) => setBills((l) => l.map((x, j) => (j === i ? { ...x, day: e.target.value } : x)))} />
                </Field>
                <Field label="Categoria">
                  <Select value={b.categoryKey} onChange={(e) => setBills((l) => l.map((x, j) => (j === i ? { ...x, categoryKey: e.target.value } : x)))}>
                    {chosenKeys.length === 0 && <option value="">Escolhida automaticamente</option>}
                    {chosenKeys.map((c) => (
                      <option key={c.key} value={c.key}>{c.name}</option>
                    ))}
                  </Select>
                </Field>
                <Button variant="ghost" size="sm" className="justify-self-start text-negative" onClick={() => setBills((l) => l.filter((_, j) => j !== i))}>
                  <Trash2 /> Remover
                </Button>
              </div>
            ))}
            <Button
              variant="outline"
              onClick={() => setBills((l) => [...l, { name: "", amount: "", day: "10", categoryKey: chosenKeys[0]?.key ?? "" }])}
            >
              <Plus /> Adicionar conta fixa
            </Button>
          </section>
        )}

        {step === 4 && (
          <section className="space-y-5" aria-labelledby="s4">
            <div>
              <h1 id="s4" className="text-2xl font-bold">Já usa uma planilha?</h1>
              <p className="text-muted-foreground">Traga seu histórico ou comece do zero. Você pode importar depois, em Configurações.</p>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <button
                type="button"
                disabled={busy}
                onClick={() => finish("import")}
                className="flex min-h-32 flex-col items-start gap-2 rounded-xl border bg-card p-5 text-left hover:bg-muted disabled:opacity-50"
              >
                <FileSpreadsheet className="size-7 text-primary-text" aria-hidden="true" />
                <span className="font-semibold">Importar minha planilha</span>
                <span className="text-sm text-muted-foreground">Excel (.xlsx, .xls) ou .csv</span>
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => finish("home")}
                className="flex min-h-32 flex-col items-start gap-2 rounded-xl border bg-card p-5 text-left hover:bg-muted disabled:opacity-50"
              >
                <Rocket className="size-7 text-primary-text" aria-hidden="true" />
                <span className="font-semibold">Começar do zero</span>
                <span className="text-sm text-muted-foreground">Lançar meus gastos a partir de hoje</span>
              </button>
            </div>
            {busy && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
                <Spinner /> Preparando sua conta…
              </p>
            )}
          </section>
        )}
      </main>

      {step < STEPS - 1 && (
        <div className="fixed inset-x-0 bottom-0 border-t bg-background px-4 pt-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]">
          <div className="mx-auto max-w-2xl">
            <Button size="lg" className="w-full" onClick={next}>
              Continuar
            </Button>
          </div>
        </div>
      )}

      <Modal
        open={customOpen}
        onOpenChange={setCustomOpen}
        title="Criar minha categoria"
        footer={
          <Button
            onClick={() => {
              const n = draft.name.trim();
              if (!n) return;
              if (customs.some((c) => c.name.toLowerCase() === n.toLowerCase())) {
                toast.error("Você já criou uma categoria com esse nome.");
                return;
              }
              setCustoms((l) => [...l, { ...draft, name: n }]);
              setCustomOpen(false);
            }}
            disabled={!draft.name.trim()}
          >
            Criar categoria
          </Button>
        }
      >
        <div className="space-y-4">
          <Field label="Nome">
            <Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} maxLength={60} autoFocus />
          </Field>
          <Field label="Grupo">
            <Select value={draft.group} onChange={(e) => setDraft({ ...draft, group: e.target.value })}>
              {EXPENSE_GROUPS.map((g) => (
                <option key={g}>{g}</option>
              ))}
            </Select>
          </Field>
          <IconPicker value={draft.icon} onChange={(icon) => setDraft({ ...draft, icon })} />
        </div>
      </Modal>
    </div>
  );
}
