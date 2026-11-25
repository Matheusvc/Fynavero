import { forwardRef, useState, type InputHTMLAttributes, type ReactNode } from "react";
import { Link } from "react-router";
import { Eye, EyeOff } from "lucide-react";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/feedback";
import { Input } from "@/components/ui/field";
import { cn } from "@/lib/utils";
import backgroundGlow from "@/assets/auth/background-glow.svg";
import character from "@/assets/auth/character.png";
import clearStroke1 from "@/assets/auth/clear-stroke-1.svg";
import clearStroke2 from "@/assets/auth/clear-stroke-2.svg";
import eyeOff from "@/assets/auth/eye-off.svg";
import tabUnderline from "@/assets/auth/tab-underline.svg";

const HEADLINE = "Controle seus gastos e veja seu dinheiro render";

/**
 * Moldura das telas de entrar e criar conta, fiel ao layout do Figma ("Login"): fundo claro com brilho laranja,
 * título à esquerda, personagem 3D ao centro e o formulário à direita. Sempre no tema claro (classe "light").
 */
export function AuthLayout({
  tab,
  title,
  subtitle,
  children,
}: {
  /** Abas "Entrar / Criar conta" acima do formulário. */
  tab?: "login" | "register";
  /** Título do lado do formulário (telas sem abas, como "Esqueci minha senha"). */
  title?: string;
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="light relative min-h-dvh overflow-hidden bg-auth-surface text-auth-ink">
      <div className="relative mx-auto flex min-h-dvh max-w-[120rem] flex-col px-6 py-6 sm:px-10 lg:px-[10.4%]">
        <header className="relative z-10">
          <Link to="/" aria-label="Fynavero, página inicial" className="inline-flex">
            <Logo className="text-2xl" />
          </Link>
        </header>

        <main className="relative grid flex-1 grid-cols-1 items-center gap-8 py-8 lg:grid-cols-[minmax(0,489fr)_minmax(0,545fr)_minmax(0,488fr)] lg:gap-0 lg:py-0">
          {/* Lado esquerdo: brilho, frase e convite para a outra aba */}
          {/* Proporções do Figma (1920 px): texto 607 (passa 118 por cima da coluna do personagem), personagem 545, respiro 88 + formulário 400 */}
          <section className="relative z-10">
            <div aria-hidden="true" className="pointer-events-none absolute -top-[5.5rem] -left-[3.5rem] h-[30.75rem] w-[26.875rem] scale-75 sm:scale-100">
              <div className="absolute inset-[-64.43%_-73.72%]">
                <img alt="" src={backgroundGlow} className="block size-full max-w-none" />
              </div>
            </div>
            <div className="relative">
              <h1 className="max-w-[38rem] text-[2.25rem] lg:w-[124.1%] lg:max-w-none leading-[1.2] font-bold sm:text-[3rem] lg:text-[clamp(2.25rem,3.05vw,3.625rem)] lg:leading-[1.33]">
                {HEADLINE}
              </h1>
              <p className="mt-6 max-w-[18.5rem] text-lg font-medium sm:text-[1.4375rem] lg:mt-16 lg:text-[clamp(1.125rem,1.2vw,1.4375rem)]">
                {tab === "register" ? (
                  <>
                    Se você já tem uma conta, pode{" "}
                    <Link to="/entrar" className="font-bold text-primary-text hover:underline">entrar aqui!</Link>
                  </>
                ) : (
                  <>
                    Se você ainda não tem uma conta, pode{" "}
                    <Link to="/cadastro" className="font-bold text-primary-text hover:underline">se cadastrar aqui!</Link>
                  </>
                )}
              </p>
            </div>
          </section>

          {/* Personagem 3D */}
          <div className="relative hidden justify-center sm:flex">
            <img
              src={character}
              alt=""
              aria-hidden="true"
              className="aspect-[545/449] w-full max-w-[22rem] object-cover lg:max-w-none"
            />
          </div>

          {/* Formulário */}
          <section className="relative z-20 w-full max-w-[25rem] justify-self-center lg:max-w-none lg:-translate-y-[4.25rem] lg:pl-[18%]">
            {tab ? (
              <AuthTabs active={tab} />
            ) : (
              <div className="mb-6">
                {title && <h2 className="text-2xl font-bold">{title}</h2>}
                {subtitle && <p className="mt-1 text-sm text-auth-field-foreground">{subtitle}</p>}
              </div>
            )}
            {children}
          </section>
        </main>
      </div>
    </div>
  );
}

/** "Entrar" (texto com traço laranja embaixo) e "Criar conta" (pílula branca), como no layout. */
function AuthTabs({ active }: { active: "login" | "register" }) {
  const item = (to: string, label: string, current: boolean) =>
    current ? (
      <Link to={to} aria-current="page" className="relative inline-flex h-[2.625rem] items-center text-[1.0625rem] font-bold tracking-[0.06em] text-primary-text">
        {label}
        <img alt="" aria-hidden="true" src={tabUnderline} width={23.5907} height={3} className="absolute -bottom-[0.2rem] left-1/2 -translate-x-1/2" />
      </Link>
    ) : (
      <Link
        to={to}
        className="inline-flex h-[2.625rem] items-center rounded-full bg-auth-pill px-[1.1rem] text-[1.0625rem] font-bold tracking-[0.06em] text-primary-text shadow-auth-pill hover:brightness-95"
      >
        {label}
      </Link>
    );
  return (
    <nav aria-label="Entrar ou criar conta" className="mb-10 flex items-center gap-[1.4rem]">
      {item("/entrar", "Entrar", active === "login")}
      {item("/cadastro", "Criar conta", active === "register")}
    </nav>
  );
}

const fieldBox = "relative flex h-[3.75rem] items-center rounded-md bg-auth-field";
const fieldInput =
  "h-full w-full rounded-md bg-transparent pl-[5%] text-[1.125rem] font-medium tracking-[0.09em] text-auth-field-foreground outline-none placeholder:text-auth-field-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring";

/** Campo azul-claro do layout, com o rótulo lido por leitores de tela e o texto de exemplo visível. */
export const AuthInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { label: string; onClear?: () => void }>(
  function AuthInput({ label, onClear, className, ...props }, ref) {
    return (
      <div className={fieldBox}>
        <input ref={ref} aria-label={label} placeholder={label} className={cn(fieldInput, onClear && "pr-14", className)} {...props} />
        {onClear && props.value && (
          <button
            type="button"
            onClick={onClear}
            aria-label={`Limpar ${label.toLowerCase()}`}
            className="absolute top-1/2 right-[5.75%] grid size-6 -translate-y-1/2 place-items-center"
          >
            <span className="relative block size-[1.15625rem] rounded-full border border-auth-placeholder" aria-hidden="true">
              <img alt="" src={clearStroke1} width={5.792} height={5.792} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />
              <img alt="" src={clearStroke2} width={5.792} height={5.792} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2" />
            </span>
          </button>
        )}
      </div>
    );
  },
);

/** Senha com o ícone de olho do layout para mostrar ou esconder. */
export function AuthPasswordInput({
  value,
  onChange,
  autoComplete,
  label = "Senha",
}: {
  value: string;
  onChange: (v: string) => void;
  autoComplete: string;
  label?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className={fieldBox}>
      <input
        aria-label={label}
        placeholder={label}
        type={show ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        autoComplete={autoComplete}
        required
        className={cn(fieldInput, "pr-14", !show && value && "text-[1.5rem] tracking-[0.15em]")}
      />
      <button
        type="button"
        onClick={() => setShow((v) => !v)}
        aria-label={show ? "Esconder senha" : "Mostrar senha"}
        aria-pressed={show}
        className={cn("absolute top-1/2 right-[6%] grid size-6 -translate-y-1/2 place-items-center", show && "opacity-60")}
      >
        <img alt="" aria-hidden="true" src={eyeOff} width={19.5938} height={16.5} />
      </button>
    </div>
  );
}

/** Botão laranja do layout (60 px, sombra suave). */
export function AuthSubmit({ busy, children }: { busy?: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="inline-flex h-[3.75rem] w-full items-center justify-center gap-2 rounded-md bg-primary text-[1.1875rem] font-bold tracking-[0.09em] text-auth-button-foreground shadow-auth-button transition hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring disabled:opacity-60"
    >
      {busy && <Spinner />}
      {children}
    </button>
  );
}

export function AuthError({ children }: { children: ReactNode }) {
  return (
    <p role="alert" className="rounded-md bg-negative-subtle px-3 py-2 text-sm text-negative">
      {children}
    </p>
  );
}

/** Campo de senha padrão do app (usado em "Criar senha nova"). */
export function PasswordInput({
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

export function GoogleButton() {
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
