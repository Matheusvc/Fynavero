import { Link } from "react-router";
import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";
import { Logo } from "@/components/Logo";

function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh bg-background">
      <header className="border-b px-4 py-3">
        <div className="mx-auto flex max-w-2xl items-center justify-between">
          <Logo className="text-lg" />
          <Link to="/" className="inline-flex min-h-touch items-center gap-1 text-sm font-medium text-primary-text">
            <ArrowLeft className="size-4" aria-hidden="true" /> Voltar
          </Link>
        </div>
      </header>
      <main className="mx-auto max-w-2xl space-y-4 px-4 py-8 text-sm leading-relaxed [&_h2]:pt-4 [&_h2]:text-lg [&_h2]:font-semibold">
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="text-muted-foreground">Última atualização: novembro de 2026.</p>
        {children}
      </main>
    </div>
  );
}

export function TermsPage() {
  return (
    <LegalLayout title="Termos de uso">
      <p>
        A Fynavero é uma ferramenta para você organizar suas finanças pessoais: registrar receitas, despesas e
        investimentos e acompanhar para onde vai o seu dinheiro.
      </p>
      <h2>1. Sua conta</h2>
      <p>Você é responsável pelas informações que lança e por manter sua senha em segredo. A conta é pessoal.</p>
      <h2>2. O que a Fynavero não faz</h2>
      <p>
        A Fynavero não movimenta dinheiro, não acessa sua conta bancária e <strong>não faz recomendação de investimento</strong>.
        Os números exibidos são calculados a partir do que você registra.
      </p>
      <h2>3. Seus dados</h2>
      <p>
        Você pode exportar todos os seus dados ou excluir sua conta definitivamente a qualquer momento em
        Configurações. Veja a <Link to="/privacidade" className="text-primary-text underline">Política de privacidade</Link>.
      </p>
      <h2>4. Disponibilidade</h2>
      <p>Trabalhamos para manter o serviço disponível, mas podem ocorrer interrupções para manutenção.</p>
    </LegalLayout>
  );
}

export function PrivacyPage() {
  return (
    <LegalLayout title="Política de privacidade">
      <p>Esta política explica como tratamos seus dados pessoais, de acordo com a Lei Geral de Proteção de Dados (LGPD).</p>
      <h2>Quais dados coletamos</h2>
      <p>Nome, e-mail, senha (guardada de forma irreversível) e os lançamentos, categorias e metas que você cadastra.</p>
      <h2>Para que usamos</h2>
      <p>Somente para oferecer o serviço: calcular seus totais, gráficos e relatórios. Não vendemos nem compartilhamos seus dados.</p>
      <h2>Planilhas importadas</h2>
      <p>A planilha é lida no seu navegador e descartada. Só os lançamentos que você confirmar ficam salvos.</p>
      <h2>Base legal e consentimento</h2>
      <p>Tratamos os dados para executar o serviço que você contratou e com o seu consentimento, registrado na criação da conta.</p>
      <h2>Seus direitos</h2>
      <p>
        Em Configurações › Privacidade e dados você pode baixar todos os seus dados (portabilidade) e excluir sua conta
        definitivamente. A exclusão apaga todos os lançamentos, categorias e configurações.
      </p>
      <h2>Segurança</h2>
      <p>Cada pessoa só acessa os próprios dados. A sessão usa cookie protegido e todas as alterações exigem token de segurança.</p>
    </LegalLayout>
  );
}
