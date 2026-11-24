import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api, errorMessage } from "@/lib/api";
import { useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthLayout, PasswordInput } from "./AuthLayout";

/** Nova senha pelo link do e-mail. Ao salvar, já entra na conta. */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const navigate = useNavigate();
  const setMe = useSetMe();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<{ password?: string; confirm?: string; form?: string }>({});
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const errs: typeof errors = {};
    if (password.length < 8) errs.password = "Use pelo menos 8 caracteres.";
    else if (password.length > 72) errs.password = "Use no máximo 72 caracteres.";
    if (confirm !== password) errs.confirm = "As senhas não são iguais.";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    try {
      const me = await api<Me>("/api/auth/password/reset", { method: "POST", body: { token, password } });
      setMe(me);
      toast.success("Senha nova salva. Você já está na sua conta.");
      navigate(me.onboardingDone ? "/" : "/boas-vindas", { replace: true });
    } catch (err) {
      setErrors({ form: errorMessage(err) });
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Link incompleto">
        <p className="text-sm">Abra o link do e-mail de novo ou peça um novo.</p>
        <Button asChild className="mt-4">
          <Link to="/esqueci-senha">Pedir novo link</Link>
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Criar senha nova" subtitle="Depois de salvar, você já entra na sua conta.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Senha nova" hint="Pelo menos 8 caracteres." error={errors.password}>
          <PasswordInput value={password} onChange={setPassword} autoComplete="new-password" />
        </Field>
        <Field label="Repita a senha nova" error={errors.confirm}>
          <PasswordInput value={confirm} onChange={setConfirm} autoComplete="new-password" />
        </Field>
        {errors.form && (
          <div role="alert" className="space-y-2 rounded-lg bg-negative-subtle px-3 py-2 text-sm text-negative">
            <p>{errors.form}</p>
            <Link to="/esqueci-senha" className="font-semibold underline underline-offset-4">
              Pedir novo link
            </Link>
          </div>
        )}
        <Button type="submit" size="lg" className="w-full" disabled={busy}>
          {busy && <Spinner />} Salvar senha nova
        </Button>
      </form>
    </AuthLayout>
  );
}
