import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { Checkbox } from "@/components/ui/field";
import { api, errorMessage } from "@/lib/api";
import { useProviders, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthError, AuthInput, AuthLayout, AuthPasswordInput, AuthSubmit, GoogleButton } from "./AuthLayout";

export function RegisterPage() {
  const navigate = useNavigate();
  const setMe = useSetMe();
  const providers = useProviders();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [terms, setTerms] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setError("Diga como podemos te chamar.");
      return;
    }
    if (password.length < 8) {
      setError("A senha precisa ter pelo menos 8 caracteres.");
      return;
    }
    if (!terms) {
      setError("Aceite os termos para continuar.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const me = await api<Me>("/api/auth/register", {
        method: "POST",
        body: { name, email, password, acceptTerms: terms },
      });
      setMe(me);
      navigate("/boas-vindas", { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout tab="register">
      <form onSubmit={submit} noValidate>
        <div className="space-y-[1.25rem]">
          <AuthInput
            label="Como podemos te chamar?"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoComplete="given-name"
            maxLength={100}
            required
          />
          <AuthInput
            label="E-mail"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onClear={() => setEmail("")}
            autoComplete="email"
            required
          />
          <AuthPasswordInput value={password} onChange={setPassword} autoComplete="new-password" label="Senha (mín. 8 caracteres)" />
        </div>
        <div className="mt-4 text-sm text-auth-field-foreground">
          <Checkbox checked={terms} onChange={setTerms}>
            Li e aceito os{" "}
            <Link to="/termos" target="_blank" className="font-medium text-primary-text underline">
              Termos de uso
            </Link>{" "}
            e a{" "}
            <Link to="/privacidade" target="_blank" className="font-medium text-primary-text underline">
              Política de privacidade
            </Link>
            .
          </Checkbox>
        </div>
        {error && (
          <div className="mt-4">
            <AuthError>{error}</AuthError>
          </div>
        )}
        <div className="mt-8">
          <AuthSubmit busy={busy}>Criar conta</AuthSubmit>
        </div>
      </form>
      {providers.data?.google && (
        <div className="mt-6 space-y-2">
          <GoogleButton />
          <p className="text-center text-xs text-auth-field-foreground">
            Ao continuar com o Google, você aceita os Termos de uso e a Política de privacidade.
          </p>
        </div>
      )}
    </AuthLayout>
  );
}
