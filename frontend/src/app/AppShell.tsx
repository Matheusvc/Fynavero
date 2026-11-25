import { NavLink, Outlet, useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  CalendarClock,
  ChartColumn,
  House,
  LogOut,
  Menu,
  Moon,
  PiggyBank,
  Plus,
  Settings,
  Sun,
  Target,
  type LucideIcon,
} from "lucide-react";
import { Logo, LogoMark } from "@/components/Logo";
import { MonthPicker } from "@/components/MonthPicker";
import { useQuickAdd } from "@/features/transactions/quick-add/QuickAddProvider";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { useMe } from "@/lib/queries";
import { useTheme } from "@/theme/theme";
import { cn } from "@/lib/utils";
import { NotificationBell } from "./NotificationBell";
import { VerifyEmailBanner } from "./VerifyEmailBanner";

type NavItem = { to: string; label: string; icon: LucideIcon; end?: boolean };

const NAV: NavItem[] = [
  { to: "/", label: "Início", icon: House, end: true },
  { to: "/lancamentos", label: "Lançamentos", icon: ArrowLeftRight },
  { to: "/parcelas", label: "Parcelas e Dívidas", icon: CalendarClock },
  { to: "/investimentos", label: "Investimentos", icon: PiggyBank },
  { to: "/objetivos", label: "Objetivos", icon: Target },
  { to: "/relatorios", label: "Relatórios", icon: ChartColumn },
  { to: "/configuracoes", label: "Configurações", icon: Settings },
];

export function useLogout() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  return async () => {
    try {
      await api("/api/auth/logout", { method: "POST" });
    } finally {
      qc.clear();
      qc.setQueryData(["me"], null);
      navigate("/entrar", { replace: true });
    }
  };
}

export function ThemeToggle({ className }: { className?: string }) {
  const { resolved, setPreference } = useTheme();
  const dark = resolved === "dark";
  return (
    <Button
      variant="ghost"
      size="icon"
      className={className}
      onClick={() => setPreference(dark ? "light" : "dark")}
      aria-label={dark ? "Usar tema claro" : "Usar tema escuro"}
    >
      {dark ? <Sun /> : <Moon />}
    </Button>
  );
}

function SideLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        cn(
          "flex min-h-touch items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors duration-(--duration-fast)",
          isActive ? "bg-accent text-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground",
        )
      }
    >
      {({ isActive }) => (
        <>
          <item.icon className={cn("size-5", isActive && "text-primary-text")} aria-hidden="true" />
          {item.label}
        </>
      )}
    </NavLink>
  );
}

function BottomLink({ to, label, icon: I, end }: NavItem) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          "flex min-h-touch flex-1 flex-col items-center justify-center gap-0.5 text-[0.6875rem] font-medium",
          isActive ? "text-foreground" : "text-muted-foreground",
        )
      }
    >
      {({ isActive }) => (
        <>
          <I className={cn("size-5", isActive && "text-primary-text")} aria-hidden="true" />
          {label}
        </>
      )}
    </NavLink>
  );
}

export function AppShell() {
  const { openNew } = useQuickAdd();
  const me = useMe();
  const logout = useLogout();

  return (
    <div className="min-h-dvh lg:pl-sidebar">
      <a
        href="#conteudo"
        className="sr-only z-50 rounded-lg bg-primary px-4 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        Pular para o conteúdo
      </a>

      <aside className="fixed inset-y-0 left-0 hidden w-sidebar flex-col border-r bg-card px-3 py-5 lg:flex">
        <div className="px-3 pb-6">
          <Logo className="text-2xl" />
        </div>
        <nav aria-label="Menu principal" className="flex flex-1 flex-col gap-1">
          {NAV.map((n) => (
            <SideLink key={n.to} item={n} />
          ))}
        </nav>
        <div className="mt-4 space-y-2 border-t pt-4">
          <p className="truncate px-3 text-sm font-medium" title={me.data?.email}>
            {me.data?.name || me.data?.email}
          </p>
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <Button variant="ghost" onClick={logout} className="flex-1 justify-start">
              <LogOut /> Sair
            </Button>
          </div>
        </div>
      </aside>

      <header className="sticky top-0 z-30 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto grid max-w-content grid-cols-[1fr_auto_1fr] items-center gap-1 px-2 py-2 sm:px-6">
          <div className="flex items-center pl-2 lg:invisible">
            <LogoMark className="min-[400px]:hidden" />
            <Logo className="hidden text-lg min-[400px]:inline-flex" />
          </div>
          <MonthPicker />
          <div className="flex items-center justify-end gap-1">
            <NotificationBell />
            <ThemeToggle className="hidden sm:inline-flex lg:hidden" />
            <Button onClick={() => openNew()} className="hidden sm:inline-flex">
              <Plus /> Lançar
            </Button>
          </div>
        </div>
      </header>
      <VerifyEmailBanner />

      <main id="conteudo" tabIndex={-1} className="mx-auto max-w-content px-4 pt-5 pb-28 outline-none sm:px-6 lg:pb-10">
        <Outlet />
      </main>

      <nav
        aria-label="Menu principal"
        className="fixed inset-x-0 bottom-0 z-30 flex h-bottom-nav items-stretch border-t bg-card pb-[env(safe-area-inset-bottom)] lg:hidden"
      >
        <BottomLink {...NAV[0]!} />
        <BottomLink {...NAV[1]!} label="Lançamentos" />
        <div className="flex flex-1 items-center justify-center">
          <button
            type="button"
            onClick={() => openNew()}
            className="-mt-6 grid size-14 place-items-center rounded-full bg-primary text-primary-foreground shadow-lg ring-4 ring-background"
            aria-label="Lançar"
          >
            <Plus className="size-7" aria-hidden="true" />
          </button>
        </div>
        <BottomLink {...NAV[3]!} />
        <BottomLink to="/mais" label="Mais" icon={Menu} />
      </nav>
    </div>
  );
}
