import { formatBRL, speakBRL } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * Valor monetário: algarismos tabulares, sempre no formato R$ 1.234,56.
 * tone="auto" pinta só valores com significado (positivo/negativo) e sempre com sinal.
 */
export function Money({
  cents,
  signed,
  tone = "none",
  className,
}: {
  cents: number;
  signed?: boolean;
  tone?: "none" | "auto" | "positive" | "negative";
  className?: string;
}) {
  const color =
    tone === "positive" || (tone === "auto" && cents > 0)
      ? "text-positive"
      : tone === "negative" || (tone === "auto" && cents < 0)
        ? "text-negative"
        : "";
  return (
    <span className={cn("money", color, className)}>
      <span aria-hidden="true">{formatBRL(cents, { signed: signed || tone === "auto" })}</span>
      <span className="sr-only">{speakBRL(cents, { signed: signed || tone === "auto" })}</span>
    </span>
  );
}
