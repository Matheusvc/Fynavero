import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { Pencil } from "lucide-react";
import { Money } from "@/components/Money";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { Modal } from "@/components/ui/dialog";
import { formatShortDay, monthName } from "@/lib/format";
import { CategoryIcon } from "@/lib/icons";
import { useCategories, useTransactions } from "@/lib/queries";
import type { Slice } from "@/lib/types";

/**
 * Lançamentos por trás de uma linha de "Para onde foi seu dinheiro".
 * Tocar num lançamento abre a edição completa (valor, categoria, parcelas…).
 */
export function CategoryPanel({
  slice,
  slices,
  month,
  contribution,
  contributionCategoryId,
  onClose,
}: {
  slice: Slice;
  slices: Slice[];
  month: string;
  contribution: number;
  contributionCategoryId?: number | null;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const { openEdit } = useQuickAdd();
  const cats = useCategories();
  const txs = useTransactions(month, { kind: "DESPESA" });
  const catById = new Map((cats.data ?? []).map((c) => [c.id, c]));
  const isOthers = slice.categoryId == null;
  const topIds = new Set(slices.map((s) => s.categoryId).filter((id): id is number => id != null));
  const list = (txs.data ?? []).filter((t) => (isOthers ? !topIds.has(t.categoryId) : t.categoryId === slice.categoryId));
  const hasContribution = !isOthers && contribution > 0 && slice.categoryId === contributionCategoryId;
  const autoOpened = useRef(false);

  useEffect(() => {
    if (autoOpened.current || !txs.data || isOthers || hasContribution || list.length !== 1) return;
    autoOpened.current = true;
    onClose();
    openEdit(list[0]!);
  }, [txs.data, isOthers, hasContribution, list, onClose, openEdit]);

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={`${slice.name} · ${monthName(month)}`} description="Toque em um lançamento para editar.">
      {txs.isLoading ? (
        <p className="flex items-center gap-2 text-sm text-muted-foreground"><Spinner /> Carregando…</p>
      ) : (
        <ul className="divide-y">
          {hasContribution && (
            <li className="flex flex-wrap items-center gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">Contribuição automática</p>
                <p className="text-xs text-muted-foreground">Calculada sobre as receitas do mês</p>
              </div>
              <Money cents={-contribution} className="font-semibold" />
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onClose();
                  navigate("/configuracoes#contribuicao");
                }}
              >
                Ajustar contribuição
              </Button>
            </li>
          )}
          {list.map((t) => {
            const c = catById.get(t.categoryId);
            return (
              <li key={t.id}>
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    openEdit(t);
                  }}
                  className="flex min-h-14 w-full items-center gap-3 py-2 text-left hover:bg-muted"
                >
                  <CategoryIcon icon={c?.icon ?? "tag"} color={c?.color} size="sm" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{t.description || c?.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {formatShortDay(t.date)}
                      {isOthers && c && ` · ${c.name}`}
                      {t.installmentNumber && ` · parcela ${t.installmentNumber}/${t.installmentTotal}`}
                      {t.status === "PREVISTO" && " · Previsto"}
                    </p>
                  </div>
                  <Money cents={-t.amountCents} className="font-semibold" />
                  <Pencil className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                </button>
              </li>
            );
          })}
          {list.length === 0 && !hasContribution && (
            <li className="py-4 text-sm text-muted-foreground">Nenhum lançamento encontrado.</li>
          )}
        </ul>
      )}
    </Modal>
  );
}
