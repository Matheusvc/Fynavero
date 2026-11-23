import { cn } from "@/lib/utils";

/** Símbolo compacto (inicial F) para espaços muito estreitos, como o topo em telas de 360px. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <span
      role="img"
      aria-label="Fynavero"
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-lg bg-primary font-logo text-base font-semibold text-primary-foreground select-none",
        className,
      )}
    >
      <span aria-hidden="true">F</span>
    </span>
  );
}

/** Logotipo Fynavero: só o nome, Montserrat SemiBold em caixa alta com espaçamento largo e ®. */
export function Logo({ className, tone = "brand" }: { className?: string; tone?: "brand" | "foreground" }) {
  return (
    <span
      className={cn(
        "inline-flex items-start font-logo font-semibold tracking-logo uppercase select-none",
        tone === "brand" ? "text-primary" : "text-foreground",
        className,
      )}
      aria-label="Fynavero"
      role="img"
    >
      <span aria-hidden="true">Fynavero</span>
      <span aria-hidden="true" className="ml-[-0.08em] text-[0.35em] leading-none font-medium">
        ®
      </span>
    </span>
  );
}
