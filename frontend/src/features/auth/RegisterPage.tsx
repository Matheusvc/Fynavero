import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { useProviders, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthLayout, GoogleButton, PasswordInput } from "./AuthLayout";

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
    if (!terms) {
      setError("Aceite os termos para continuar.");
      return;
    }
    if (password.length < 8) {
      setError("A senha precisa ter pelo menos 8 caracteres.");
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
    <AuthLayout title="Criar sua conta" subtitle="Leva menos de 3 minutos para deixar tudo do seu jeito.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Como podemos te chamar?">
          <Input value={name} onChange={(e) => setName(e.target.value)} autoComplete="given-name" maxLength={100} required />
        </Field>
        <Field label="E-mail">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
        </Field>
        <Field label="Senha" hint="Pelo menos 8 caracteres.">
          <PasswordInput value={password} onChange={setPassword} autoComplete="new-password" />
        </Field>
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
        {error && (
          <p role="alert" className="rounded-lg bg-negative-subtle px-3 py-2 text-sm text-negative">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy}>
          {busy && <Spinner />} Criar conta
        </Button>
      </form>
      {providers.data?.google && (
        <div className="mt-4 space-y-2">
          <GoogleButton />
          <p className="text-center text-xs text-muted-foreground">
            Ao continuar com o Google, você aceita os Termos de uso e a Política de privacidade.
          </p>
        </div>
      )}
      <p className="mt-8 text-center text-sm">
        Já tem conta?{" "}
        <Link to="/entrar" className="font-semibold text-primary-text underline-offset-4 hover:underline">
          Entrar
        </Link>
      </p>
    </AuthLayout>
  );
}
