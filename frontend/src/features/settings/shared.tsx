import { useState, type ReactNode } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Card } from "@/components/ui/card";
import { Spinner } from "@/components/ui/feedback";
import { errorMessage } from "@/lib/api";

export function useSave() {
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

export function Section({ id, title, description, children, action }: {
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

export function ConfirmModal({ open, onClose, title, body, confirm, onConfirm, busy }: {
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
