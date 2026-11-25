import { Check, RotateCcw } from "lucide-react";
import { PALETTE, cssColor } from "@/lib/colors";
import { cn } from "@/lib/utils";

/**
 * Escolha de cor entre as 12 da paleta, como bolinhas (grupo de rádio acessível).
 * Com `defaultToken`, aparece também "Padrão" (valor nulo), que mostra a cor padrão.
 */
export function ColorPicker({
  value,
  onChange,
  label,
  defaultToken,
  className,
  ...aria
}: {
  value: string | null | undefined;
  onChange: (token: string | null) => void;
  label: string;
  defaultToken?: string;
  className?: string;
  id?: string;
  "aria-describedby"?: string;
}) {
  const swatch = (token: string | null, name: string, color: string) => {
    const selected = (value ?? null) === token;
    return (
      <button
        key={token ?? "padrao"}
        type="button"
        role="radio"
        aria-checked={selected}
        aria-label={name}
        title={name}
        onClick={() => onChange(token)}
        className={cn(
          "relative grid size-8 place-items-center rounded-full border border-border transition-transform hover:scale-110",
          "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
          selected && "ring-2 ring-foreground ring-offset-2 ring-offset-card",
        )}
        style={{ backgroundColor: color }}
      >
        {token === null && (
          <RotateCcw className="size-3.5 text-background mix-blend-difference" aria-hidden="true" />
        )}
        {selected && token !== null && <Check className="size-4 text-background mix-blend-difference" aria-hidden="true" />}
      </button>
    );
  };

  return (
    <div {...aria} role="radiogroup" aria-label={label} className={cn("flex flex-wrap gap-2", className)}>
      {defaultToken && swatch(null, "Cor padrão", cssColor(defaultToken))}
      {PALETTE.map((c) => swatch(c.token, c.name, cssColor(c.token)))}
    </div>
  );
}

/** Bolinha com a cor (para listas). */
export function ColorDot({ token, className }: { token: string; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn("inline-block size-2.5 shrink-0 rounded-full", className)}
      style={{ backgroundColor: cssColor(token) }}
    />
  );
}
