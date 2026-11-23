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
import { centsToInput, defaultDateFor, formatBRL, parseMoneyInput } from "@/lib/format";
import { KIND_LABEL, PAYMENT_METHODS } from "@/lib/labels";
import { useMonth } from "@/lib/month";
import { useCategories, useTxMutations } from "@/lib/queries";
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

  const [kind, setKind] = useState<Kind>(tx?.kind ?? opts.kind ?? "DESPESA");
  const [amount, setAmount] = useState(tx ? centsToInput(tx.amountCents) : "");
  const [categoryId, setCategoryId] = useState<number | null>(tx?.categoryId ?? opts.categoryId ?? null);
  const [date, setDate] = useState(tx?.date ?? defaultDateFor(month));
  const [description, setDescription] = useState(tx?.description ?? "");
  const [payment, setPayment] = useState<PaymentMethod | "">(tx?.paymentMethod ?? "");
  const [note, setNote] = useState(tx?.note ?? "");
  const [repeat, setRepeat] = useState(false);
  const [parcelado, setParcelado] = useState(!!opts.installments);
  const [count, setCount] = useState("2");
  const [more, setMore] = useState(editing || !!opts.installments);
  const [errors, setErrors] = useState<{ amount?: string; category?: string; count?: string }>({});
  const [newCat, setNewCat] = useState<string | null>(null);
  const [creatingCat, setCreatingCat] = useState(false);

  const list = useMemo(
    () => (cats.data ?? []).filter((c) => c.kind === kind && (!c.hidden || c.id === categoryId)),
    [cats.data, kind, categoryId],
  );
  const cents = parseMoneyInput(amount);
  const n = Number(count);
  const busy = m.create.isPending || m.update.isPending;

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
    if (parcelado && (!Number.isInteger(n) || n < 2 || n > 120)) errs.count = "Use de 2 a 120 parcelas.";
    setErrors(errs);
    if (Object.keys(errs).length) {
      if (errs.amount) amountRef.current?.focus();
      return;
    }
    try {
      if (tx) {
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
        toast.success("Lançamento atualizado");
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
          installments: parcelado ? n : null,
        });
        toast.success(
          parcelado
            ? `${created.length} parcelas criadas`
            : repeat
              ? "Lançamento salvo e repetido nos próximos meses"
              : "Lançamento salvo",
        );
      }
      onClose();
    } catch (err) {
      toast.error(errorMessage(err));
    }
  }

  const title = editing ? "Editar lançamento" : "Novo lançamento";
  const isRecurring = !!tx?.recurrenceId;
  const isInstallment = !!tx?.installmentPlanId;

  return (
    <Modal
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={title}
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

        <Field
          label={parcelado ? "Valor total da compra" : "Valor"}
          error={errors.amount}
          hint={parcelado && cents && n >= 2 ? `${n}× de ${formatBRL(Math.floor(cents / n))}` : undefined}
        >
          <CurrencyInput ref={amountRef} value={amount} onChange={setAmount} large />
        </Field>

        {cats.isLoading ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Spinner /> Carregando categorias…
          </div>
        ) : (
          <CategoryPicker
            label="Categoria"
            categories={list}
            value={categoryId}
            onChange={setCategoryId}
            error={errors.category}
          />
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
                <Field label={parcelado ? "Data da 1ª parcela" : "Data"}>
                  <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
                </Field>
                <Field label="Forma de pagamento">
                  <Select value={payment} onChange={(e) => setPayment(e.target.value as PaymentMethod | "")}>
                    <option value="">Não informar</option>
                    {PAYMENT_METHODS.map((p) => (
                      <option key={p.value} value={p.value}>
                        {p.label}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <Field label="Descrição">
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  maxLength={120}
                  placeholder="Ex.: Feira do sábado"
                />
              </Field>
              <Field label="Observação">
                <Textarea value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} rows={2} />
              </Field>
              {!editing && (
                <>
                  <Switch
                    label="Repetir todo mês"
                    description="Cria este lançamento nos próximos meses como Previsto."
                    checked={repeat}
                    onChange={(v) => {
                      setRepeat(v);
                      if (v) setParcelado(false);
                    }}
                  />
                  {kind === "DESPESA" && (
                    <Switch
                      label="Compra parcelada"
                      description="As próximas parcelas entram como Previsto nos meses seguintes."
                      checked={parcelado}
                      onChange={(v) => {
                        setParcelado(v);
                        if (v) setRepeat(false);
                      }}
                    />
                  )}
                  {parcelado && (
                    <Field label="Número de parcelas" error={errors.count}>
                      <Input
                        type="number"
                        inputMode="numeric"
                        min={2}
                        max={120}
                        value={count}
                        onChange={(e) => setCount(e.target.value)}
                      />
                    </Field>
                  )}
                </>
              )}
            </div>
          )}
        </div>
      </form>
    </Modal>
  );
}
