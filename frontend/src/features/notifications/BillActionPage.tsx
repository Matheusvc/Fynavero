import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { CircleAlert, CircleCheck } from "lucide-react";
import { Logo } from "@/components/Logo";
import { Money } from "@/components/Money";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { formatDate } from "@/lib/format";
import type { BillAction } from "@/lib/types";

/**
 * Página aberta pelo botão "Marcar como paga" do e-mail de lembrete. Funciona sem login: mostra a conta e
 * pede confirmação (assim leitores de e-mail que abrem links sozinhos não marcam nada).
 */
export default function BillActionPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [bill, setBill] = useState<BillAction | null>(null);
  const [error, setError] = useState<string | null>(token ? null : "Link incompleto. Abra a Fynavero para marcar a conta como paga.");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!token) return;
    api<BillAction>("/api/actions/describe", { method: "POST", body: { token } })
      .then(setBill)
      .catch((e) => setError(errorMessage(e)));
  }, [token]);

  async function pay() {
    setBusy(true);
    try {
      setBill(await api<BillAction>("/api/actions/pay", { method: "POST", body: { token } }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-dvh flex-col items-center bg-background px-4 pt-10 pb-12">
      <Logo className="mb-8 text-2xl" />
      <Card className="w-full max-w-sm text-center">
        {error ? (
          <div className="flex flex-col items-center gap-3 py-4">
            <CircleAlert className="size-8 text-negative" aria-hidden="true" />
            <p>{error}</p>
            <Button asChild variant="outline">
              <Link to="/">Abrir a Fynavero</Link>
            </Button>
          </div>
        ) : !bill ? (
          <div className="py-8" aria-busy="true">
            <Spinner className="mx-auto size-6" />
          </div>
        ) : bill.paid ? (
          <div className="flex flex-col items-center gap-3 py-4">
            <CircleCheck className="size-10 text-positive" aria-hidden="true" />
            <h1 className="text-xl font-bold">Conta paga</h1>
            <p className="text-muted-foreground">
              {bill.description} (<Money cents={bill.amountCents} />) está marcada como paga.
            </p>
            <Button asChild>
              <Link to="/">Abrir a Fynavero</Link>
            </Button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-3 py-2">
            <h1 className="text-xl font-bold">Marcar como paga?</h1>
            <p className="text-lg font-semibold">{bill.description}</p>
            <Money cents={bill.amountCents} className="text-3xl font-bold" />
            <p className="text-sm text-muted-foreground">Vencimento: {formatDate(bill.date)}</p>
            <Button size="lg" className="mt-2 w-full" onClick={pay} disabled={busy}>
              {busy ? <Spinner /> : <CircleCheck />} Sim, já paguei
            </Button>
            <Button asChild variant="ghost" className="w-full">
              <Link to="/">Agora não</Link>
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
}
