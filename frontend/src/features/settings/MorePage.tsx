import { Link } from "react-router";
import { CalendarClock, ChartColumn, ChevronRight, FileSpreadsheet, LogOut, Settings as SettingsIcon, Target } from "lucide-react";
import { ThemeToggle, useLogout } from "@/app/AppShell";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";

export function MorePage() {
  const logout = useLogout();
  const links = [
    { to: "/parcelas", label: "Parcelas e Dívidas", icon: CalendarClock },
    { to: "/objetivos", label: "Objetivos", icon: Target },
    { to: "/relatorios", label: "Relatórios", icon: ChartColumn },
    { to: "/importar", label: "Importar planilha", icon: FileSpreadsheet },
    { to: "/configuracoes", label: "Configurações", icon: SettingsIcon },
  ];
  return (
    <div>
      <PageHeader title="Mais" />
      <Card className="p-0 sm:p-0">
        <ul className="divide-y">
          {links.map((l) => (
            <li key={l.to}>
              <Link to={l.to} className="flex min-h-14 items-center gap-3 px-4 hover:bg-muted">
                <l.icon className="size-5 text-primary-text" aria-hidden="true" />
                <span className="flex-1 font-medium">{l.label}</span>
                <ChevronRight className="size-4 text-muted-foreground" aria-hidden="true" />
              </Link>
            </li>
          ))}
          <li className="flex min-h-14 items-center gap-3 px-4">
            <span className="flex-1 font-medium">Tema</span>
            <ThemeToggle />
          </li>
          <li>
            <button type="button" onClick={logout} className="flex min-h-14 w-full items-center gap-3 px-4 text-left hover:bg-muted">
              <LogOut className="size-5 text-negative" aria-hidden="true" />
              <span className="flex-1 font-medium">Sair</span>
            </button>
          </li>
        </ul>
      </Card>
    </div>
  );
}
