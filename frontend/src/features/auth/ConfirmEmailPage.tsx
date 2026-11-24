import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { CircleCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import type { Me } from "@/lib/types";
import { AuthLayout } from "./AuthLayout";

/** Página do link "Confirmar meu e-mail". Funciona com ou sem login. */
export function ConfirmEmailPage() {
  const [params] = useSearchParams();
  const qc = useQueryClient();
  const [state, setState] = useState<"loading" | "done" | "error">("loading");
  const [error, setError] = useState("");
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const token = params.get("token");
    if (!token) {
      setError("Link incompleto. Abra o link do e-mail de novo.");
      setState("error");
      return;
    }
    api<Me>("/api/auth/email/verify", { method: "POST", body: { token } })
      .then(() => {
        setState("done");
        void qc.invalidateQueries({ queryKey: ["me"] });
      })
      .catch((e) => {
        setError(errorMessage(e));
        setState("error");
      });
  }, [params, qc]);

  return (
    <AuthLayout title={state === "done" ? "E-mail confirmado" : state === "error" ? "Não deu certo" : "Confirmando…"}>
      {state === "loading" && (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner /> Conferindo seu link…
        </p>
      )}
      {state === "done" && (
        <div className="space-y-4">
          <p className="flex gap-2 text-sm">
            <CircleCheck className="size-5 shrink-0 text-positive" aria-hidden="true" />
            Pronto! Agora você recebe os lembretes de contas, os alertas de limite e o resumo do mês.
          </p>
          <Button asChild>
            <Link to="/">Ir para a Fynavero</Link>
          </Button>
        </div>
      )}
      {state === "error" && (
        <div className="space-y-4">
          <p role="alert" className="text-sm">{error}</p>
          <Button asChild variant="outline">
            <Link to="/">Abrir a Fynavero</Link>
          </Button>
        </div>
      )}
    </AuthLayout>
  );
}
