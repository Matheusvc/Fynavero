import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { Spinner } from "@/components/ui/feedback";
import { api } from "@/lib/api";
import type { Me } from "@/lib/types";
import { Section, useSave } from "../shared";

export function SecuritySection({ me }: { me: Me }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const s = useSave();
  return (
    <Section id="seguranca" title={me.hasPassword ? "Trocar senha" : "Criar senha"}>
      <form
        className="grid grid-cols-1 gap-4 sm:grid-cols-2"
        onSubmit={async (e) => {
          e.preventDefault();
          if (await s.run(() => api("/api/profile/password", { method: "PUT", body: { current: current || null, next } }), "Senha atualizada")) {
            setCurrent("");
            setNext("");
          }
        }}
      >
        {me.hasPassword && (
          <Field label="Senha atual">
            <Input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" required />
          </Field>
        )}
        <Field label="Nova senha" hint="Pelo menos 8 caracteres.">
          <Input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" minLength={8} required />
        </Field>
        <div>
          <Button type="submit" disabled={s.busy || next.length < 8}>{s.busy && <Spinner />} Salvar senha</Button>
        </div>
      </form>
    </Section>
  );
}
