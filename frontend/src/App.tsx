import { lazy, Suspense, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router";
import { AppShell } from "@/components/AppShell";
import { Logo } from "@/components/Logo";
import { Button } from "@/components/ui/button";
import { ErrorState, Spinner } from "@/components/ui/misc";
import { errorMessage } from "@/lib/api";
import { useMe } from "@/lib/queries";
import { LoginPage, MagicLinkPage, RegisterPage } from "@/pages/Auth";
import { PrivacyPage, TermsPage } from "@/pages/Legal";
import HomePage from "@/pages/Home";
import TransactionsPage from "@/pages/Transactions";

const OnboardingPage = lazy(() => import("@/pages/Onboarding"));
const InstallmentsPage = lazy(() => import("@/pages/Installments"));
const InvestmentsPage = lazy(() => import("@/pages/Investments"));
const ReportsPage = lazy(() => import("@/pages/Reports"));
const ImportPage = lazy(() => import("@/pages/Import"));
const SettingsPage = lazy(() => import("@/pages/Settings"));
const MorePage = lazy(() => import("@/pages/Settings").then((m) => ({ default: m.MorePage })));
const DesignSystemPage = lazy(() => import("@/pages/DesignSystemPage"));

function Splash() {
  return (
    <div className="grid min-h-dvh place-items-center" aria-busy="true">
      <div className="flex flex-col items-center gap-4">
        <Logo className="text-3xl" />
        <Spinner className="size-6 text-muted-foreground" />
      </div>
    </div>
  );
}

function RequireAuth({ children, onboarding }: { children: ReactNode; onboarding?: boolean }) {
  const me = useMe();
  const location = useLocation();
  if (me.isLoading) return <Splash />;
  if (me.isError) {
    return (
      <div className="mx-auto max-w-md p-6">
        <ErrorState message={errorMessage(me.error)} onRetry={() => me.refetch()} />
      </div>
    );
  }
  if (!me.data) return <Navigate to="/entrar" replace state={{ from: location.pathname }} />;
  if (!onboarding && !me.data.onboardingDone) return <Navigate to="/boas-vindas" replace />;
  if (onboarding && me.data.onboardingDone) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function PublicOnly({ children }: { children: ReactNode }) {
  const me = useMe();
  if (me.isLoading) return <Splash />;
  if (me.data) return <Navigate to={me.data.onboardingDone ? "/" : "/boas-vindas"} replace />;
  return <>{children}</>;
}

function NotFound() {
  return (
    <div className="flex flex-col items-center gap-4 py-16 text-center">
      <h1 className="text-2xl font-bold">Página não encontrada</h1>
      <p className="text-muted-foreground">O endereço pode ter mudado.</p>
      <Button asChild>
        <a href="/">Ir para o Início</a>
      </Button>
    </div>
  );
}

export default function App() {
  return (
    <Suspense fallback={<Splash />}>
      <Routes>
        <Route path="/entrar" element={<PublicOnly><LoginPage /></PublicOnly>} />
        <Route path="/cadastro" element={<PublicOnly><RegisterPage /></PublicOnly>} />
        <Route path="/entrar/link" element={<MagicLinkPage />} />
        <Route path="/termos" element={<TermsPage />} />
        <Route path="/privacidade" element={<PrivacyPage />} />
        <Route path="/design-system" element={<DesignSystemPage />} />
        <Route path="/boas-vindas" element={<RequireAuth onboarding><OnboardingPage /></RequireAuth>} />
        <Route element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route index element={<HomePage />} />
          <Route path="lancamentos" element={<TransactionsPage />} />
          <Route path="parcelas" element={<InstallmentsPage />} />
          <Route path="investimentos" element={<InvestmentsPage />} />
          <Route path="relatorios" element={<ReportsPage />} />
          <Route path="importar" element={<ImportPage />} />
          <Route path="configuracoes" element={<SettingsPage />} />
          <Route path="mais" element={<MorePage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
