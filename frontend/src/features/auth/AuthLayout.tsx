import { useState, type ReactNode } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/app/AppShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";

export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: ReactNode; children: ReactNode }) {
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
