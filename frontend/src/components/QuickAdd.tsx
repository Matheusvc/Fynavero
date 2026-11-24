import { createContext, useCallback, useContext, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ChevronDown, Copy, CircleCheck, Plus, Trash2 } from "lucide-react";
import { Modal } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Field, Input, Select, Switch, Textarea } from "@/components/ui/field";
import { Segmented, Spinner } from "@/components/ui/misc";
import { CurrencyInput } from "./CurrencyInput";
import { CategoryPicker } from "./CategoryPicker";
import { api, errorMessage } from "@/lib/api";
import { centsToInput, defaultDateFor, formatBRL, monthLabel, parseMoneyInput, shiftMonth } from "@/lib/format";
import { KIND_LABEL, PAYMENT_METHODS } from "@/lib/labels";
import { useMonth } from "@/lib/month";
import { invalidateFinance, useCategories, useTxMutations } from "@/lib/queries";
import type { Category, Kind, PaymentMethod, Scope, Tx } from "@/lib/types";
import { cn } from "@/lib/utils";

type NewOptions = { kind?: Kind; installments?: boolean; categoryId?: number };

type Ctx = {
  openNew: (o?: NewOptions) => void;
  openEdit: (tx: Tx) => void;
  deleteWithUndo: (tx: Tx) => Promise<void>;
};

const QuickAddCtx = createContext<Ctx | null>(null);

export function useQuickAdd() {
  const c = useContext(QuickAddCtx);
  if (!c) throw new Error("useQuickAdd precisa de QuickAddProvider");
  return c;
}

type ScopeAsk = { action: "edit" | "delete"; installment: boolean; resolve: (s: Scope | null) => void };

export function QuickAddProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ open: boolean; tx: Tx | null; opts: NewOptions; key: number }>({
    open: false,
    tx: null,
    opts: {},
    key: 0,
  });
  const [ask, setAsk] = useState<ScopeAsk | null>(null);
  const m = useTxMutations();

  const askScope = useCallback(
    (tx: Tx, action: "edit" | "delete") =>
      new Promise<Scope | null>((resolve) => {
        if (!tx.recurrenceId && !tx.installmentPlanId) return resolve("ONE");
        setAsk({ action, installment: !!tx.installmentPlanId, resolve });
      }),
    [],
  );

  const deleteWithUndo = useCallback(
    async (tx: Tx) => {
      const scope = await askScope(tx, "delete");
      if (!scope) return;
      try {
        const { ids } = await m.remove.mutateAsync({ id: tx.id, scope });
        toast(ids.length > 1 ? `${ids.length} lançamentos excluídos` : "Lançamento excluído", {
          duration: 4000,
          action: {
            label: "Desfazer",
            onClick: () => {
              m.restore.mutate(ids, {
                onSuccess: () => toast.success("Exclusão desfeita"),
                onError: (e) => toast.error(errorMessage(e)),
              });
            },
          },
        });
      } catch (e) {
        toast.error(errorMessage(e));
      }
    },
    [askScope, m.remove, m.restore],
  );

  const value = useMemo<Ctx>(
    () => ({
      openNew: (opts = {}) => setState((s) => ({ open: true, tx: null, opts, key: s.key + 1 })),
      openEdit: (tx) => setState((s) => ({ open: true, tx, opts: {}, key: s.key + 1 })),
      deleteWithUndo,
    }),
    [deleteWithUndo],
  );

  const close = () => setState((s) => ({ ...s, open: false }));

  return (
    <QuickAddCtx.Provider value={value}>
      {children}
      {state.key > 0 && (
        <TxSheet
          key={state.key}
          open={state.open}
          tx={state.tx}
          opts={state.opts}
          onClose={close}
          askScope={askScope}
          onDelete={async (tx) => {
            close();
            await deleteWithUndo(tx);
          }}
        />
      )}
      <Modal
        open={!!ask}
        onOpenChange={(o) => {
          if (!o) {
            ask?.resolve(null);
            setAsk(null);
          }
        }}
        title={ask?.installment ? "Este lançamento é parcelado" : "Este lançamento se repete todo mês"}
        description={
          ask?.action === "delete" ? "O que você quer excluir?" : "Onde você quer aplicar esta alteração?"
        }
        footer={
          <>
            <Button
              variant="outline"
              onClick={() => {
                ask?.resolve("FOLLOWING");
                setAsk(null);
              }}
            >
              Este e os próximos
            </Button>
            <Button
              variant={ask?.action === "delete" ? "destructive" : "primary"}
              onClick={() => {
                ask?.resolve("ONE");
                setAsk(null);
              }}
            >
              Só este
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">
          {ask?.installment
            ? "“Este e os próximos” vale para esta parcela e as seguintes."
            : "“Este e os próximos” também muda os meses seguintes."}
        </p>
      </Modal>
    </QuickAddCtx.Provider>
  );
}

function addMonthsISO(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const target = new Date(Date.UTC(y!, m! - 1 + n, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d!, last));
  return target.toISOString().slice(0, 10);
}

function monthsBetween(fromYm: string, toYm: string): number {
  const [ay, am] = fromYm.split("-").map(Number);
  const [by, bm] = toYm.split("-").map(Number);
  return (by! - ay!) * 12 + (bm! - am!);
}

/** Número de parcelas e mês da última parcela, ligados: mudar um recalcula o outro. */
function InstallmentCountFields({
  firstMonth,
  count,
  onCount,
  error,
  min = 2,
  label = "Número de parcelas",
}: {
  firstMonth: string;
  count: string;
  onCount: (v: string) => void;
  error?: string;
  min?: number;
  label?: string;
}) {
  const n = Number(count);
  const lastMonth = Number.isInteger(n) && n >= 1 ? shiftMonth(firstMonth, n - 1) : "";
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <Field label={label} error={error}>
        <Input type="number" inputMode="numeric" min={min} max={120} value={count} onChange={(e) => onCount(e.target.value)} />
      </Field>
      <Field label="Mês da última parcela" hint={lastMonth ? monthLabel(lastMonth) : undefined}>
        <Input
          type="month"
          min={shiftMonth(firstMonth, min - 1)}
          value={lastMonth}
          onChange={(e) => {
            if (!e.target.value) return;
            onCount(String(Math.max(min, monthsBetween(firstMonth, e.target.value) + 1)));
          }}
        />
      </Field>
    </div>
  );
}

function TxSheet({
  open,
  tx,
  opts,
  onClose,
  askScope,
  onDelete,
}: {
  open: boolean;
  tx: Tx | null;
  opts: NewOptions;
  onClose: () => void;
  askScope: (tx: Tx, action: "edit" | "delete") => Promise<Scope | null>;
  onDelete: (tx: Tx) => void;
}) {
  const { month } = useMonth();
  const qc = useQueryClient();
  const cats = useCategories();
  const m = useTxMutations();
  const amountRef = useRef<HTMLInputElement>(null);
  const editing = !!tx;
  const initialDate = tx?.date ?? defaultDateFor(month);

  const [kind, setKind] = useState<Kind>(tx?.kind ?? opts.kind ?? "DESPESA");
  const [amount, setAmount] = useState(tx ? centsToInput(tx.amountCents) : "");
  const [categoryId, setCategoryId] = useState<number | null>(tx?.categoryId ?? opts.categoryId ?? null);
  const [date, setDate] = useState(initialDate);
  const [description, setDescription] = useState(tx?.description ?? "");
  const [payment, setPayment] = useState<PaymentMethod | "">(tx?.paymentMethod ?? (opts.installments ? "CREDITO" : ""));
  const [note, setNote] = useState(tx?.note ?? "");
  const [repeat, setRepeat] = useState(false);
  const [parcelado, setParcelado] = useState(!!opts.installments);
  const [count, setCount] = useState("2");
  const [valueMode, setValueMode] = useState<"total" | "parcela">("total");
  const [planCount, setPlanCount] = useState(String(tx?.installmentTotal ?? ""));
  const [loanOn, setLoanOn] = useState(true);
  const [loanCount, setLoanCount] = useState("12");
  const [loanAmount, setLoanAmount] = useState("");
  const [loanFirst, setLoanFirst] = useState(addMonthsISO(initialDate, 1));
  const [loanCat, setLoanCat] = useState("");
  const [more, setMore] = useState(editing || !!opts.installments);
  const [errors, setErrors] = useState<{ amount?: string; category?: string; count?: string; loan?: string }>({});
  const [newCat, setNewCat] = useState<string | null>(null);
  const [creatingCat, setCreatingCat] = useState(false);
  const [saving, setSaving] = useState(false);

  const all = cats.data ?? [];
  const list = useMemo(
    () => all.filter((c) => c.kind === kind && (!c.hidden || c.id === categoryId)),
    [all, kind, categoryId],
  );
  const expenseCats = all.filter((c) => c.kind === "DESPESA" && !c.hidden);
  const selected = all.find((c) => c.id === categoryId);
  const cents = parseMoneyInput(amount);
  const n = Number(count);

  const isInstallment = !!tx?.installmentPlanId;
  const isRecurring = !!tx?.recurrenceId;
  const canConvert = editing && tx!.kind === "DESPESA" && !isInstallment && !isRecurring;
  const parcelAllowed = editing ? canConvert : kind === "DESPESA" && !repeat;
  const showParcelBlock = parcelAllowed && (payment === "CREDITO" || parcelado);
  const effectiveParcel = showParcelBlock && parcelado;
  const isLoan = !editing && kind === "RECEITA" && !!selected?.loan;
  const loanCents = parseMoneyInput(loanAmount);
  const loanN = Number(loanCount);
  const busy = saving || m.create.isPending || m.update.isPending;

  const planFirstMonth = isInstallment ? shiftMonth(tx!.date.slice(0, 7), -((tx!.installmentNumber ?? 1) - 1)) : "";

  async function createCategory() {
    const name = newCat?.trim();
    if (!name) return;
    setCreatingCat(true);
    try {
      const c = await api<Category>("/api/categories", { method: "POST", body: { name, kind, group: "Outros" } });
      await qc.invalidateQueries({ queryKey: ["categories"] });
      setCategoryId(c.id);
      setNewCat(null);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setCreatingCat(false);
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs: typeof errors = {};
    if (!cents || cents <= 0) errs.amount = "Informe um valor maior que zero.";
    if (!categoryId) errs.category = "Escolha uma categoria.";
    if (effectiveParcel && (!Number.isInteger(n) || n < 2 || n > 120)) errs.count = "Use de 2 a 120 parcelas.";
    if (isInstallment && (!Number.isInteger(Number(planCount)) || Number(planCount) < 1 || Number(planCount) > 120)) {
      errs.count = "Use de 1 a 120 parcelas.";
    }
    if (isLoan && loanOn && (!loanCents || !Number.isInteger(loanN) || loanN < 1 || loanN > 240)) {
      errs.loan = "Informe quantas parcelas e o valor de cada uma.";
    }
    setErrors(errs);
    if (Object.keys(errs).length) {
      if (errs.amount) amountRef.current?.focus();
      if (errs.count || errs.loan) setMore(true);
      return;
    }
    setSaving(true);
    try {
      if (tx && canConvert && effectiveParcel) {
        const created = await api<Tx[]>(`/api/transactions/${tx.id}/installments`, {
          method: "POST",
          body: {
            amountCents: cents!,
            categoryId: categoryId!,
            date,
            description: description || undefined,
            paymentMethod: payment || null,
            note: note || undefined,
            count: n,
            installmentValue: valueMode === "parcela",
          },
        });
        invalidateFinance(qc);
        toast.success(`Lançamento parcelado em ${created.length}×`);
      } else if (tx) {
        const scope = await askScope(tx, "edit");
        if (!scope) return;
        await m.update.mutateAsync({
          id: tx.id,
          scope,
          body: {
            amountCents: cents!,
            categoryId: categoryId!,
            date,
            description: description || undefined,
            paymentMethod: payment || null,
            note: note || undefined,
          },
        });
        if (isInstallment && Number(planCount) !== tx.installmentTotal) {
          await api(`/api/installment-plans/${tx.installmentPlanId}`, { method: "PUT", body: { count: Number(planCount) } });
          invalidateFinance(qc);
          toast.success(`Lançamento atualizado e parcelamento ajustado para ${planCount}×`);
        } else {
          toast.success("Lançamento atualizado");
        }
      } else {
        const created = await m.create.mutateAsync({
          kind,
          amountCents: cents!,
          categoryId: categoryId!,
          date,
          description: description || undefined,
          paymentMethod: payment || null,
          note: note || undefined,
          repeatMonthly: repeat,
          installments: effectiveParcel ? n : null,
          installmentValue: effectiveParcel && valueMode === "parcela",
          loan:
            isLoan && loanOn
              ? { count: loanN, installmentCents: loanCents!, firstDate: loanFirst, categoryId: loanCat ? Number(loanCat) : null }
              : null,
        });
        toast.success(
          effectiveParcel
            ? `${created.length} parcelas criadas`
            : isLoan && loanOn
              ? `Empréstimo registrado e ${created.length - 1} parcelas de pagamento criadas`
              : repeat
                ? "Lançamento salvo e repetido nos próximos meses"
                : "Lançamento salvo",
        );
      }
      onClose();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  const amountLabel = effectiveParcel
    ? valueMode === "total"
      ? "Valor total da compra"
      : "Valor de cada parcela"
    : isLoan
      ? "Valor recebido"
      : "Valor";
  const amountHint =
    effectiveParcel && cents && n >= 2
      ? valueMode === "total"
        ? `${n}× de ${formatBRL(Math.floor(cents / n))}`
        : `${n}× de ${formatBRL(cents)} = ${formatBRL(cents * n)} no total`
      : undefined;

  const parcelFields = (
    <div className="space-y-4 rounded-lg bg-muted p-3">
      {payment === "CREDITO" && (
        <Segmented
          label="Pagamento no crédito"
          value={parcelado ? "parcelado" : "avista"}
          onChange={(v) => setParcelado(v === "parcelado")}
          options={[
            { value: "avista", label: "À vista" },
            { value: "parcelado", label: "Parcelado" },
          ]}
        />
      )}
      {parcelado && (
        <>
          <Segmented
            label="O valor informado é"
            value={valueMode}
            onChange={setValueMode}
            options={[
              { value: "total", label: "Total da compra" },
              { value: "parcela", label: "Valor da parcela" },
            ]}
          />
          <InstallmentCountFields firstMonth={date.slice(0, 7)} count={count} onCount={setCount} error={errors.count} />
          <p className="text-xs text-muted-foreground">
            {canConvert
              ? "Este lançamento vira a 1ª parcela e as próximas entram como Previsto nos meses seguintes."
              : "A 1ª parcela fica na data acima e as próximas entram como Previsto em cada mês, até a última."}
          </p>
        </>
      )}
    </div>
  );

  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={editing ? "Editar lançamento" : "Novo lançamento"}
      description={
        isInstallment
          ? `Parcela ${tx!.installmentNumber}/${tx!.installmentTotal}`
          : isRecurring
            ? "Lançamento que se repete todo mês"
            : undefined
      }
      initialFocus={() => (editing ? null : amountRef.current)}
      footer={
        <>
          {editing && (
            <div className="flex flex-1 flex-wrap gap-2">
              <Button variant="ghost" onClick={() => onDelete(tx!)} className="text-negative">
                <Trash2 /> Excluir
              </Button>
              <Button
                variant="ghost"
                onClick={async () => {
                  try {
                    await m.duplicate.mutateAsync(tx!.id);
                    toast.success("Lançamento duplicado para hoje");
                    onClose();
                  } catch (e) {
                    toast.error(errorMessage(e));
                  }
                }}
              >
                <Copy /> Duplicar
              </Button>
              {tx!.status === "PREVISTO" && (
                <Button
                  variant="ghost"
                  onClick={async () => {
                    try {
                      await m.markPaid.mutateAsync(tx!.id);
                      toast.success("Marcado como pago");
                      onClose();
                    } catch (e) {
                      toast.error(errorMessage(e));
                    }
                  }}
                >
                  <CircleCheck /> Marcar como pago
                </Button>
              )}
            </div>
          )}
          <Button type="submit" form="tx-form" size="lg" disabled={busy}>
            {busy && <Spinner />}
            {editing ? "Salvar alterações" : "Salvar lançamento"}
          </Button>
        </>
      }
    >
      <form id="tx-form" onSubmit={submit} className="space-y-5" noValidate>
        {!editing && (
          <Segmented
            label="Tipo de lançamento"
            value={kind}
            onChange={(k) => {
              setKind(k);
              setCategoryId(null);
              if (k !== "DESPESA") setParcelado(false);
            }}
            options={(["DESPESA", "RECEITA", "INVESTIMENTO"] as Kind[]).map((k) => ({ value: k, label: KIND_LABEL[k] }))}
          />
        )}

        <Field label={amountLabel} error={errors.amount} hint={amountHint}>
          <CurrencyInput ref={amountRef} value={amount} onChange={setAmount} large />
        </Field>

        {cats.isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Spinner /> Carregando categorias…
          </div>
        ) : (
          <CategoryPicker label="Categoria" categories={list} value={categoryId} onChange={setCategoryId} error={errors.category} />
        )}
        {newCat === null ? (
          <Button variant="link" onClick={() => setNewCat("")}>
            <Plus /> Criar categoria
          </Button>
        ) : (
          <div className="flex items-end gap-2">
            <Field label="Nome da nova categoria" className="flex-1">
              <Input
                value={newCat}
                onChange={(e) => setNewCat(e.target.value)}
                maxLength={60}
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    void createCategory();
                  }
                }}
              />
            </Field>
            <Button variant="outline" onClick={createCategory} disabled={creatingCat || !newCat.trim()}>
              Criar
            </Button>
          </div>
        )}

        {isLoan && (
          <section aria-labelledby="loan-title" className="space-y-4 rounded-lg border p-3">
            <h3 id="loan-title" className="text-sm font-semibold">Pagamento do empréstimo</h3>
            <Switch
              label="Já registrar as parcelas do pagamento"
              description="As parcelas entram como despesa Previsto e aparecem em Parcelas e Dívidas."
              checked={loanOn}
              onChange={setLoanOn}
            />
            {loanOn && (
              <>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <Field label="Número de parcelas">
                    <Input type="number" inputMode="numeric" min={1} max={240} value={loanCount} onChange={(e) => setLoanCount(e.target.value)} />
                  </Field>
                  <Field label="Valor de cada parcela">
                    <CurrencyInput value={loanAmount} onChange={setLoanAmount} />
                  </Field>
                  <Field label="Primeira parcela">
                    <Input type="date" value={loanFirst} onChange={(e) => setLoanFirst(e.target.value)} />
                  </Field>
                  <Field label="Categoria das parcelas">
                    <Select value={loanCat} onChange={(e) => setLoanCat(e.target.value)}>
                      <option value="">Empréstimos e financiamentos</option>
                      {expenseCats
                        .filter((c) => c.name !== "Empréstimos e financiamentos")
                        .map((c) => (
                          <option key={c.id} value={c.id}>
                            {c.name}
                          </option>
                        ))}
                    </Select>
                  </Field>
                </div>
                {errors.loan && (
                  <p role="alert" className="text-sm text-negative">{errors.loan}</p>
                )}
                {loanCents && loanN >= 1 && (
                  <p className="text-sm">
                    Total a pagar: <strong className="money">{formatBRL(loanCents * loanN)}</strong>
                    {cents && loanCents * loanN > cents && (
                      <span className="text-muted-foreground"> · custo do empréstimo: {formatBRL(loanCents * loanN - cents)}</span>
                    )}
                    <span className="block text-xs text-muted-foreground">
                      Última parcela em {monthLabel(shiftMonth(loanFirst.slice(0, 7), loanN - 1))}.
                    </span>
                  </p>
                )}
              </>
            )}
          </section>
        )}

        {isInstallment && (
          <section aria-labelledby="plan-title" className="space-y-3 rounded-lg border p-3">
            <h3 id="plan-title" className="text-sm font-semibold">
              Parcelamento · parcela {tx!.installmentNumber} de {tx!.installmentTotal}
            </h3>
            <InstallmentCountFields
              firstMonth={planFirstMonth}
              count={planCount}
              onCount={setPlanCount}
              error={errors.count}
              min={1}
              label="Número total de parcelas"
            />
            <p className="text-xs text-muted-foreground">
              Aumentar cria as parcelas que faltam com o mesmo valor; diminuir remove as últimas parcelas ainda não vencidas.
            </p>
          </section>
        )}

        <div className="rounded-lg border">
          <button
            type="button"
            onClick={() => setMore((v) => !v)}
            aria-expanded={more}
            aria-controls="tx-more"
            className="flex min-h-touch w-full items-center justify-between px-3 text-sm font-medium"
          >
            Mais detalhes
            <ChevronDown className={cn("size-4 transition-transform", more && "rotate-180")} aria-hidden="true" />
          </button>
          {more && (
            <div id="tx-more" className="space-y-4 border-t p-3">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <Field label={effectiveParcel ? "Data da 1ª parcela" : isLoan ? "Data em que recebeu" : "Data"}>
                  <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
                </Field>
                <Field label="Forma de pagamento">
                  <Select
                    value={payment}
                    onChange={(e) => {
                      const v = e.target.value as PaymentMethod | "";
                      setPayment(v);
                      if (v !== "CREDITO" && !opts.installments) setParcelado(false);
                    }}
                  >
                    <option value="">Não informar</option>
                    {PAYMENT_METHODS.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              {showParcelBlock && parcelFields}
              {parcelAllowed && payment !== "CREDITO" && (
                <Switch
                  label={canConvert ? "Parcelar este lançamento" : "Compra parcelada"}
                  description="As próximas parcelas entram como Previsto nos meses seguintes."
                  checked={parcelado}
                  onChange={(v) => {
                    setParcelado(v);
                    if (v) setRepeat(false);
                  }}
                />
              )}
              <Field label="Descrição">
                <Input value={description} onChange={(e) => setDescription(e.target.value)} maxLength={120} placeholder="Ex.: Feira do sábado" />
              </Field>
              <Field label="Observação">
                <Textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} rows={2} />
              </Field>
              {!editing && !isLoan && (
                <Switch
                  label="Repetir todo mês"
                  description="Cria este lançamento nos próximos meses como Previsto."
                  checked={repeat}
                  onChange={(v) => {
                    setRepeat(v);
                    if (v) setParcelado(false);
                  }}
                />
              )}
            </div>
          )}
        </div>
      </form>
    </Modal>
  );
}
