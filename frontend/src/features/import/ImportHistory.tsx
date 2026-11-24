import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { History, Undo2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Card, CardTitle } from "@/components/ui/card";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { invalidateFinance, useImports } from "@/lib/queries";
import type { ImportBatch } from "@/lib/types";

export function ImportHistory() {
  const qc = useQueryClient();
  const batches = useImports();
  const [confirm, setConfirm] = useState<ImportBatch | null>(null);
  const [busy, setBusy] = useState(false);
  if (!batches.data || batches.data.length === 0) return null;
  return (
    <Card aria-labelledby="hist">
      <CardTitle id="hist">
        <span className="inline-flex items-center gap-2"><History className="size-4" aria-hidden="true" /> Importações anteriores</span>
      </CardTitle>
      <ul className="divide-y">
        {batches.data.map((b) => (
          <li key={b.id} className="flex flex-wrap items-center gap-3 py-3 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium">{b.fileName}</p>
              <p className="text-xs text-muted-foreground">
                {new Date(b.createdAt).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" })} · {b.rowCount} linhas
              </p>
            </div>
            {b.undoneAt ? (
              <Badge>Desfeita</Badge>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setConfirm(b)}>
                <Undo2 /> Desfazer
              </Button>
            )}
          </li>
        ))}
      </ul>
      <Modal
        open={!!confirm}
        onOpenChange={(o) => !o && setConfirm(null)}
        title="Desfazer importação?"
        description={confirm ? `Remove os ${confirm.rowCount} lançamentos importados de “${confirm.fileName}”. Os demais não são afetados.` : undefined}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirm(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={busy}
              onClick={async () => {
                if (!confirm) return;
                setBusy(true);
                try {
                  await api(`/api/imports/${confirm.id}`, { method: "DELETE" });
                  invalidateFinance(qc);
                  toast.success("Importação desfeita");
                  setConfirm(null);
                } catch (e) {
                  toast.error(errorMessage(e));
                } finally {
                  setBusy(false);
                }
              }}
            >
              {busy && <Spinner />} Desfazer importação
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted-foreground">Essa ação não apaga a planilha do seu computador.</p>
      </Modal>
    </Card>
  );
}
