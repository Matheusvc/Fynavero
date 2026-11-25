import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { api, errorMessage } from "@/lib/api";
import { useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";
import { AuthError, AuthLayout, AuthPasswordInput, AuthSubmit } from "./AuthLayout";

/** Nova senha pelo link do e-mail. Ao salvar, já entra na conta. */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const navigate = useNavigate();
  const setMe = useSetMe();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [linkInvalid, setLinkInvalid] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 8 || password.length > 72) return setError("Use de 8 a 72 caracteres na senha nova.");
    if (confirm !== password) return setError("As senhas não são iguais.");
    setBusy(true);
    setError(null);
    try {
      const me = await api<Me>("/api/auth/password/reset", { method: "POST", body: { token, password } });
      setMe(me);
      toast.success("Senha nova salva. Você já está na sua conta.");
      navigate(me.onboardingDone ? "/" : "/boas-vindas", { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setLinkInvalid(true);
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Link incompleto" subtitle="Abra o link do e-mail de novo ou peça um novo.">
        <Link to="/esqueci-senha" className="font-semibold text-primary-text hover:underline">Pedir novo link</Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Criar senha nova" subtitle="Depois de salvar, você já entra na sua conta.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <AuthPasswordInput value={password} onChange={setPassword} autoComplete="new-password" label="Senha nova (mín. 8 caracteres)" />
        <AuthPasswordInput value={confirm} onChange={setConfirm} autoComplete="new-password" label="Repita a senha nova" />
        {error && (
          <AuthError>
            {error}{" "}
            {linkInvalid && (
              <Link to="/esqueci-senha" className="font-semibold underline underline-offset-4">Pedir novo link</Link>
            )}
          </AuthError>
        )}
        <AuthSubmit busy={busy}>Salvar senha nova</AuthSubmit>
      </form>
    </AuthLayout>
  );
}
