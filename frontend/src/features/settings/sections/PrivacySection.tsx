import { useState } from "react";
import { Link } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { Download, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Modal } from "@/components/ui/dialog";
import { Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import type { Me } from "@/lib/types";
import { Section, useSave } from "../shared";

export function PrivacySection({ me }: { me: Me }) {
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState("");
  const [password, setPassword] = useState("");
  const s = useSave();
  return (
    <Section id="privacidade" title="Privacidade e dados" description="Seus direitos pela LGPD.">
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" asChild>
          <a href="/api/profile/export" download>
            <Download /> Exportar meus dados
          </a>
        </Button>
        <Button variant="ghost" asChild><Link to="/termos">Termos de uso</Link></Button>
        <Button variant="ghost" asChild><Link to="/privacidade">Política de privacidade</Link></Button>
      </div>
      <div className="mt-6 rounded-lg border border-negative p-4">
        <h3 className="font-semibold text-negative">Excluir conta</h3>
        <p className="mt-1 text-sm text-muted-foreground">Apaga definitivamente todos os seus lançamentos, categorias e configurações. Não dá para desfazer.</p>
        <Button variant="destructive" className="mt-3" onClick={() => setOpen(true)}>
          <Trash2 /> Excluir minha conta
        </Button>
      </div>
      <Modal
        open={open}
        onOpenChange={setOpen}
        title="Excluir conta definitivamente?"
        description="Todos os seus dados serão apagados. Esta ação não pode ser desfeita."
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={s.busy || confirm.trim().toUpperCase() !== "EXCLUIR" || (me.hasPassword && !password)}
              onClick={async () => {
                if (await s.run(() => api("/api/profile", { method: "DELETE", body: { confirm, password: password || null } }), "Conta excluída")) {
                  qc.clear();
                  qc.setQueryData(["me"], null);
                  window.location.assign("/entrar");
                }
              }}
            >
              {s.busy && <Spinner />} Excluir definitivamente
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Digite EXCLUIR para confirmar">
            <Input value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="off" />
          </Field>
          {me.hasPassword && (
            <Field label="Sua senha">
              <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
            </Field>
          )}
        </div>
      </Modal>
    </Section>
  );
}
