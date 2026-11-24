import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { Switch } from "@/components/ui/field";
import { api, errorMessage } from "@/lib/api";
import { monthLabel } from "@/lib/format";
import { useNotificationPreferences } from "@/lib/queries";
import type { NotificationPreferences } from "@/lib/types";
import { Section } from "../shared";

type Prefs = Pick<NotificationPreferences, "bills" | "limits" | "summary">;

/** Quais avisos chegam por e-mail. No app, o sino mostra sempre as contas e os limites. */
export function NotificationsSection({ email }: { email: string }) {
  const qc = useQueryClient();
  const prefs = useNotificationPreferences();
  const [sending, setSending] = useState(false);

  async function sendExample() {
    setSending(true);
    try {
      const r = await api<{ month: string; emailConfigured: boolean }>("/api/notifications/test-summary", { method: "POST" });
      if (r.emailConfigured) toast.success(`Enviamos o resumo de ${monthLabel(r.month)} para ${email}`);
      else toast.info("O resumo foi gerado, mas o envio de e-mails ainda não está ativo neste servidor.");
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setSending(false);
    }
  }

  async function toggle(change: Partial<Prefs>) {
    if (!prefs.data) return;
    const next = { bills: prefs.data.bills, limits: prefs.data.limits, summary: prefs.data.summary, ...change };
    qc.setQueryData(["notification-preferences"], { ...prefs.data, ...next });
    try {
      const saved = await api<NotificationPreferences>("/api/notifications/preferences", { method: "PUT", body: next });
      qc.setQueryData(["notification-preferences"], saved);
    } catch (e) {
      toast.error(errorMessage(e));
      void prefs.refetch();
    }
  }

  return (
    <Section
      id="notificacoes"
      title="Notificações"
      description={<>Avisos enviados para <strong>{email}</strong>. No app, o sino no topo da tela mostra contas e limites a qualquer hora.</>}
    >
      {!prefs.data ? (
        <Spinner />
      ) : (
        <div className="space-y-4">
          <Switch
            label="Contas que vencem amanhã"
            description="Um e-mail às 8h do dia anterior, com o botão “Marcar como paga”."
            checked={prefs.data.bills}
            onChange={(bills) => toggle({ bills })}
          />
          <Switch
            label="Limites de categoria"
            description="Quando os gastos de uma categoria chegam a 80% e a 100% do limite do mês."
            checked={prefs.data.limits}
            onChange={(limits) => toggle({ limits })}
          />
          <Switch
            label="Resumo do mês"
            description="No dia 1, quanto entrou, quanto saiu, a maior categoria de gasto e quanto sobrou."
            checked={prefs.data.summary}
            onChange={(summary) => toggle({ summary })}
          />
          <div className="flex flex-wrap items-center gap-3 border-t pt-4">
            <Button
              variant="outline"
              disabled={sending}
              onClick={sendExample}
            >
              {sending ? <Spinner /> : <Mail />} Enviar um exemplo do resumo
            </Button>
            {!prefs.data.emailConfigured && (
              <p className="text-xs text-muted-foreground">O envio de e-mails ainda não está ativo neste servidor.</p>
            )}
          </div>
        </div>
      )}
    </Section>
  );
}

