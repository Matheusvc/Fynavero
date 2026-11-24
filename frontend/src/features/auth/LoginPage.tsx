import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { useProviders, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthLayout, GoogleButton, PasswordInput } from "./AuthLayout";

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const setMe = useSetMe();
  const providers = useProviders();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(params.get("erro") === "google" ? "Não foi possível entrar com o Google." : null);
  const [busy, setBusy] = useState(false);
  const [magic, setMagic] = useState<"idle" | "sending" | "sent">("idle");

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const me = await api<Me>("/api/auth/login", { method: "POST", body: { email, password } });
      setMe(me);
      navigate(me.onboardingDone ? "/" : "/boas-vindas", { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  async function sendMagic() {
    if (!email) {
      setError("Digite seu e-mail para receber o link.");
      return;
    }
    setMagic("sending");
    setError(null);
    try {
      await api("/api/auth/magic-link", { method: "POST", body: { email } });
      setMagic("sent");
    } catch (err) {
      setMagic("idle");
      setError(errorMessage(err));
    }
  }

  return (
    <AuthLayout title="Entrar" subtitle="Bom te ver de novo.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="E-mail">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </Field>
        <Field label="Senha">
          <PasswordInput value={password} onChange={setPassword} autoComplete="current-password" />
        </Field>
        {error && (
          <p role="alert" className="rounded-lg bg-negative-subtle px-3 py-2 text-sm text-negative">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy}>
          {busy && <Spinner />} Entrar
        </Button>
      </form>

      <div className="my-6 flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" /> ou <span className="h-px flex-1 bg-border" />
      </div>

      <div className="space-y-3">
        {providers.data?.magicLink && (
          <Button variant="outline" size="lg" className="w-full" onClick={sendMagic} disabled={magic === "sending"}>
            {magic === "sending" ? <Spinner /> : <Mail />} Receber link de acesso por e-mail
          </Button>
        )}
        {magic === "sent" && (
          <p role="status" className="rounded-lg bg-positive-subtle px-3 py-2 text-sm text-positive">
            Se houver uma conta com esse e-mail, enviamos um link que vale por 15 minutos.
          </p>
        )}
        {providers.data?.google && <GoogleButton />}
      </div>

      <p className="mt-8 text-center text-sm">
        Ainda não tem conta?{" "}
        <Link to="/cadastro" className="font-semibold text-primary-text underline-offset-4 hover:underline">
          Criar conta grátis
        </Link>
      </p>
    </AuthLayout>
  );
}
