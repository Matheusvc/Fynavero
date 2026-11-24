import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthLayout } from "./AuthLayout";

export function MagicLinkPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const setMe = useSetMe();
  const [error, setError] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const token = params.get("token");
    if (!token) {
      setError("Link incompleto. Peça um novo na tela de entrar.");
      return;
    }
    api<Me>("/api/auth/magic-link/consume", { method: "POST", body: { token } })
      .then((me) => {
        setMe(me);
        navigate(me.onboardingDone ? "/" : "/boas-vindas", { replace: true });
      })
      .catch((e) => setError(errorMessage(e)));
  }, [params, navigate, setMe]);

  return (
    <AuthLayout title={error ? "Não deu certo" : "Entrando…"}>
      {error ? (
        <div className="space-y-4">
          <p role="alert" className="text-sm">{error}</p>
          <Button asChild>
            <Link to="/entrar">Voltar para entrar</Link>
          </Button>
        </div>
      ) : (
        <p className="flex items-center gap-2 text-sm text-muted-foreground">
          <Spinner /> Conferindo seu link…
        </p>
      )}
    </AuthLayout>
  );
}
