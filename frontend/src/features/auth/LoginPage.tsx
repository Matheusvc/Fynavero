import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { api, errorMessage } from "@/lib/api";
import { useProviders, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthError, AuthInput, AuthLayout, AuthPasswordInput, AuthSubmit, GoogleButton } from "./AuthLayout";

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
    <AuthLayout tab="login">
      <form onSubmit={submit} noValidate>
        <div className="space-y-[1.25rem]">
          <AuthInput
            label="E-mail"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onClear={() => setEmail("")}
            autoComplete="email"
            required
          />
          <AuthPasswordInput value={password} onChange={setPassword} autoComplete="current-password" />
        </div>
        <p className="mt-3 text-right">
          <Link to="/esqueci-senha" className="text-sm font-medium tracking-[0.1em] text-auth-hint hover:text-primary-text hover:underline">
            Esqueci minha senha
          </Link>
        </p>
        {error && (
          <div className="mt-4">
            <AuthError>{error}</AuthError>
          </div>
        )}
        <div className="mt-[3.25rem]">
          <AuthSubmit busy={busy}>Entrar</AuthSubmit>
        </div>
      </form>

      <div className="mt-6 space-y-3 text-center">
        {providers.data?.magicLink && magic !== "sent" && (
          <button
            type="button"
            onClick={sendMagic}
            disabled={magic === "sending"}
            className="text-sm font-medium text-auth-hint underline-offset-4 hover:text-primary-text hover:underline disabled:opacity-60"
          >
            {magic === "sending" ? "Enviando link…" : "Prefiro receber um link de acesso por e-mail"}
          </button>
        )}
        {magic === "sent" && (
          <p role="status" className="rounded-md bg-positive-subtle px-3 py-2 text-sm text-positive">
            Se houver uma conta com esse e-mail, enviamos um link que vale por 15 minutos.
          </p>
        )}
        {providers.data?.google && <GoogleButton />}
      </div>
    </AuthLayout>
  );
}
