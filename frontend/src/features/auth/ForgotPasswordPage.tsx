import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { MailCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { AuthLayout } from "./AuthLayout";

/** "Esqueci minha senha": pede o e-mail e envia o link de redefinição (válido por 1 hora). */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) {
      setError("Digite o e-mail da sua conta.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api("/api/auth/password/forgot", { method: "POST", body: { email: email.trim() } });
      setSent(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout title="Esqueci minha senha" subtitle="Enviamos um link para você criar uma senha nova.">
      {sent ? (
        <div className="space-y-4">
          <div role="status" className="flex gap-3 rounded-lg bg-positive-subtle px-3 py-3 text-sm text-positive">
            <MailCheck className="size-5 shrink-0" aria-hidden="true" />
            <p>
              Se houver uma conta com <strong>{email.trim()}</strong>, o link já está a caminho. Ele vale por 1 hora. Confira também a caixa de
              spam.
            </p>
          </div>
          <Button variant="outline" className="w-full" onClick={() => setSent(false)}>
            Usar outro e-mail
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="space-y-4" noValidate>
          <Field label="E-mail da conta">
            <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required autoFocus />
          </Field>
          {error && (
            <p role="alert" className="rounded-lg bg-negative-subtle px-3 py-2 text-sm text-negative">
              {error}
            </p>
          )}
          <Button type="submit" size="lg" className="w-full" disabled={busy}>
            {busy && <Spinner />} Enviar link
          </Button>
        </form>
      )}
      <p className="mt-8 text-center text-sm">
        <Link to="/entrar" className="font-semibold text-primary-text underline-offset-4 hover:underline">
          Voltar para entrar
        </Link>
      </p>
    </AuthLayout>
  );
}
