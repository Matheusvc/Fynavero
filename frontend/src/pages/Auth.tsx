import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { Eye, EyeOff, Mail } from "lucide-react";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/AppShell";
import { Button } from "@/components/ui/button";
import { Checkbox, Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/misc";
import { api, errorMessage } from "@/lib/api";
import { useProviders, useSetMe } from "@/lib/queries";
import type { Me } from "@/lib/types";

function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-background">
      <div className="flex items-center justify-between px-4 py-3">
        <Logo className="text-xl" />
        <ThemeToggle />
      </div>
      <main className="flex flex-1 items-start justify-center px-4 pt-6 pb-12 sm:items-center sm:pt-0">
        <div className="w-full max-w-sm">
          <h1 className="text-2xl font-bold">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>
      </main>
    </div>
  );
}

function PasswordInput({
  value,
  onChange,
  autoComplete,
  ...aria
}: {
  value: string;
  onChange: (v: string) => void;
  autoComplete: string;
  id?: string;
  "aria-describedby"?: string;
  "aria-invalid"?: boolean;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className="relative">
      <Input
        {...aria}
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        className="pr-12"
        required
      />
      <button
        type="button"
        onClick={() => setShow((v) => !v)}
        className="absolute top-0 right-0 grid size-touch place-items-center text-muted-foreground"
        aria-label={show ? "Esconder senha" : "Mostrar senha"}
      >
        {show ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
      </button>
    </div>
  );
}

function GoogleButton() {
  return (
    <Button asChild variant="outline" size="lg" className="w-full">
      <a href="/oauth2/authorization/google">
        <svg viewBox="0 0 24 24" aria-hidden="true" className="size-5">
          <path fill="currentColor" d="M21.35 11.1H12v2.98h5.35c-.23 1.4-1.66 4.1-5.35 4.1-3.22 0-5.85-2.67-5.85-5.96S8.78 6.26 12 6.26c1.83 0 3.06.78 3.76 1.45l2.56-2.47C16.73 3.76 14.6 2.8 12 2.8 6.92 2.8 2.8 6.92 2.8 12s4.12 9.2 9.2 9.2c5.31 0 8.83-3.73 8.83-8.99 0-.6-.07-1.06-.15-1.51Z" />
        </svg>
        Continuar com Google
      </a>
    </Button>
  );
}

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
