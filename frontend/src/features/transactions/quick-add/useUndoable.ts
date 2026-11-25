import { toast } from "sonner";
import { errorMessage } from "@/lib/api";
import { useTxMutations } from "@/lib/queries";
import type { Scope } from "@/lib/types";

/** Tempo para o "Desfazer" depois de excluir ou marcar como pago. */
const UNDO_MS = 8000;

/**
 * Ações que dá para desfazer por 8 segundos: excluir lançamentos, excluir uma compra parcelada inteira
 * e marcar como pago. Um só lugar para o aviso com "Desfazer".
 */
export function useUndoable() {
  const m = useTxMutations();
  const fail = (e: unknown) => toast.error(errorMessage(e));

  /** Aviso com "Desfazer" para itens excluídos (volta com POST /api/transactions/restore). */
  const restorable = (message: string, ids: number[]) =>
    toast(message, {
      duration: UNDO_MS,
      action: {
        label: "Desfazer",
        onClick: () => m.restore.mutate(ids, { onSuccess: () => toast.success("Exclusão desfeita"), onError: fail }),
      },
    });

  async function deleteTx(id: number, scope: Scope) {
    try {
      const { ids } = await m.remove.mutateAsync({ id, scope });
      restorable(ids.length > 1 ? `${ids.length} lançamentos excluídos` : "Lançamento excluído", ids);
    } catch (e) {
      fail(e);
    }
  }

  function deletePlan(planId: number, name: string) {
    m.removePlan.mutate(planId, {
      onSuccess: ({ ids }) => restorable(`${name} excluído (${ids.length} ${ids.length === 1 ? "parcela" : "parcelas"})`, ids),
      onError: fail,
    });
  }

  function markPaid(id: number, name?: string) {
    m.markPaid.mutate(id, {
      onSuccess: () =>
        toast.success(name ? `${name} marcado como pago` : "Marcado como pago", {
          duration: UNDO_MS,
          action: {
            label: "Desfazer",
            onClick: () =>
              m.markUnpaid.mutate(id, { onSuccess: () => toast.success("Pronto: voltou para os próximos vencimentos"), onError: fail }),
          },
        }),
      onError: fail,
    });
  }

  return { deleteTx, deletePlan, markPaid, busy: m.markPaid.isPending || m.removePlan.isPending };
}
