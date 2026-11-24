import { toast } from "sonner";
import { errorMessage } from "@/lib/api";
import { useTxMutations } from "@/lib/queries";
import { UNDO_MS } from "./QuickAddProvider";

/** Ações que dá para desfazer por alguns segundos: marcar como pago e excluir uma compra parcelada inteira. */
export function useUndoable() {
  const m = useTxMutations();

  function markPaid(id: number, name?: string) {
    m.markPaid.mutate(id, {
      onSuccess: () =>
        toast.success(name ? `${name} marcado como pago` : "Marcado como pago", {
          duration: UNDO_MS,
          action: {
            label: "Desfazer",
            onClick: () =>
              m.markUnpaid.mutate(id, {
                onSuccess: () => toast.success("Pronto: voltou para os próximos vencimentos"),
                onError: (e) => toast.error(errorMessage(e)),
              }),
          },
        }),
      onError: (e) => toast.error(errorMessage(e)),
    });
  }

  function deletePlan(planId: number, name: string) {
    m.removePlan.mutate(planId, {
      onSuccess: ({ ids }) =>
        toast(`${name} excluído (${ids.length} ${ids.length === 1 ? "parcela" : "parcelas"})`, {
          duration: UNDO_MS,
          action: {
            label: "Desfazer",
            onClick: () =>
              m.restore.mutate(ids, {
                onSuccess: () => toast.success("Exclusão desfeita"),
                onError: (e) => toast.error(errorMessage(e)),
              }),
          },
        }),
      onError: (e) => toast.error(errorMessage(e)),
    });
  }

  return { markPaid, deletePlan, busy: m.markPaid.isPending || m.removePlan.isPending };
}
