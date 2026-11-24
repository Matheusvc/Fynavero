import { useEffect } from "react";
import { Link, useLocation } from "react-router";
import { FileSpreadsheet, LogOut } from "lucide-react";
import { useLogout } from "@/app/AppShell";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { Spinner } from "@/components/ui/feedback";
import { ImportHistory } from "@/features/import/ImportHistory";
import { useMe } from "@/lib/queries";
import { Section } from "./shared";
import { ProfileSection } from "./sections/ProfileSection";
import { CategoriesSection } from "./sections/CategoriesSection";
import { IncomeSection } from "./sections/IncomeSection";
import { BillsSection } from "./sections/BillsSection";
import { ContributionSection } from "./sections/ContributionSection";
import { AppearanceSection } from "./sections/AppearanceSection";
import { NotificationsSection } from "./sections/NotificationsSection";
import { SecuritySection } from "./sections/SecuritySection";
import { PrivacySection } from "./sections/PrivacySection";

const SECTIONS = [
  ["perfil", "Perfil"],
  ["categorias", "Categorias"],
  ["renda", "Fontes de renda"],
  ["contas-fixas", "Contas fixas"],
  ["contribuicao", "Contribuição"],
  ["notificacoes", "Notificações"],
  ["importar", "Importar planilha"],
  ["aparencia", "Aparência"],
  ["seguranca", "Senha"],
  ["privacidade", "Privacidade e dados"],
] as const;

export default function SettingsPage() {
  const me = useMe();
  const logout = useLogout();
  const { hash } = useLocation();
  const ready = !!me.data;
  useEffect(() => {
    if (!ready || !hash) return;
    document.getElementById(hash.slice(1))?.scrollIntoView({ block: "start" });
  }, [ready, hash]);
  if (!me.data) return <Spinner />;
  return (
    <div>
      <PageHeader title="Configurações" actions={<Button variant="ghost" onClick={logout}><LogOut /> Sair</Button>} />
      <nav aria-label="Seções" className="mb-4 flex gap-2 overflow-x-auto pb-1">
        {SECTIONS.map(([id, label]) => (
          <a key={id} href={`#${id}`} className="inline-flex min-h-9 shrink-0 items-center rounded-full border bg-card px-3 text-sm hover:bg-muted">
            {label}
          </a>
        ))}
      </nav>
      <div className="space-y-4">
        <ProfileSection me={me.data} />
        <CategoriesSection />
        <IncomeSection />
        <BillsSection />
        <ContributionSection me={me.data} />
        <NotificationsSection email={me.data.email} />
        <Section id="importar" title="Importar planilha" description="Traga lançamentos de uma planilha do Excel ou .csv. Cada importação pode ser desfeita.">
          <Button asChild><Link to="/importar"><FileSpreadsheet /> Importar planilha</Link></Button>
        </Section>
        <ImportHistory />
        <AppearanceSection />
        <SecuritySection me={me.data} />
        <PrivacySection me={me.data} />
      </div>
    </div>
  );
}
