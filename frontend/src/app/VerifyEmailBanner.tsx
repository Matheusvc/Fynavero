import { useState } from "react";
import { toast } from "sonner";
import { MailWarning, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { useMe } from "@/lib/queries";

const DISMISS_KEY = "fyn-verify-banner-dismissed";

/** Faixa no topo enquanto o e-mail não for confirmado: sem confirmar, nenhum aviso sai por e-mail. */
export function VerifyEmailBanner() {
  const me = useMe();
  const [busy, setBusy] = useState(false);
  const [hidden, setHidden] = useState(() => {
    try {
      return sessionStorage.getItem(DISMISS_KEY) === "1";
    } catch {
      return false;
    }
  });
  if (!me.data || me.data.emailVerified || hidden) return null;

  async function resend() {
    setBusy(true);
    try {
      await api("/api/auth/email/resend", { method: "POST" });
      toast.success(`Enviamos um novo link para ${me.data?.email}. Confira também o spam.`);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  function dismiss() {
    setHidden(true);
    try {
      sessionStorage.setItem(DISMISS_KEY, "1");
    } catch {
      // sem armazenamento, a faixa só volta na próxima visita
    }
  }

  return (
    <div role="status" className="border-b bg-accent text-accent-foreground">
      <div className="mx-auto flex max-w-content items-center gap-3 px-4 py-2 text-sm sm:px-6">
        <MailWarning className="size-4 shrink-0" aria-hidden="true" />
        <p className="min-w-0 flex-1">
          Confirme seu e-mail (<strong className="break-all">{me.data.email}</strong>) para receber lembretes e o resumo do mês.
        </p>
        <Button size="sm" variant="outline" onClick={resend} disabled={busy}>
          {busy && <Spinner />} Reenviar
        </Button>
        <button type="button" onClick={dismiss} className="grid size-8 place-items-center rounded-full hover:bg-muted" aria-label="Fechar aviso">
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
