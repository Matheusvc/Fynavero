import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import type { Scope, Tx } from "@/lib/types";
import { TransactionSheet, type NewOptions } from "./TransactionSheet";
import { useUndoable } from "./useUndoable";

/**
 * Lançamento rápido disponível em todo o app: abrir para lançar ou editar, perguntar
 * "Só este / Este e os próximos / Todas as parcelas" e excluir com "Desfazer".
 */
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
  const undoable = useUndoable();

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
      if (scope) await undoable.deleteTx(tx.id, scope);
    },
    [askScope, undoable],
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
  const answer = (s: Scope) => {
    ask?.resolve(s);
    setAsk(null);
  };

  return (
    <QuickAddCtx.Provider value={value}>
      {children}
      {state.key > 0 && (
        <TransactionSheet
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
          ask?.installment && ask.action === "delete" ? (
            <div className="flex w-full flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <Button variant="outline" onClick={() => answer("ONE")}>
                Só esta parcela
              </Button>
              <Button variant="outline" onClick={() => answer("FOLLOWING")}>
                Esta e as próximas
              </Button>
              <Button variant="destructive" onClick={() => answer("ALL")}>
                Todas as parcelas
              </Button>
            </div>
          ) : (
            <>
              <Button variant="outline" onClick={() => answer("FOLLOWING")}>
                {ask?.installment ? "Esta e as próximas" : "Este e os próximos"}
              </Button>
              <Button variant={ask?.action === "delete" ? "destructive" : "primary"} onClick={() => answer("ONE")}>
                {ask?.installment ? "Só esta parcela" : "Só este"}
              </Button>
            </>
          )
        }
      >
        <p className="text-sm text-muted-foreground">
          {ask?.installment && ask.action === "delete"
            ? "“Todas as parcelas” apaga a compra inteira de Parcelas e Dívidas. “Só esta parcela” apaga só a deste mês. Em qualquer caso dá para desfazer logo depois."
            : ask?.installment
              ? "“Esta e as próximas” vale para esta parcela e as seguintes."
              : "“Este e os próximos” também muda os meses seguintes."}
        </p>
      </Modal>
    </QuickAddCtx.Provider>
  );
}
