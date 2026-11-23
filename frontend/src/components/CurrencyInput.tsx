import { forwardRef, type InputHTMLAttributes } from "react";
import { centsToInput, parseMoneyInput } from "@/lib/format";
import { cn } from "@/lib/utils";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange"> & {
  value: string;
  onChange: (text: string) => void;
  large?: boolean;
};

/** Campo de valor em reais: abre o teclado numérico e normaliza para "1.234,56" ao sair. */
export const CurrencyInput = forwardRef<HTMLInputElement, Props>(function CurrencyInput(
  { value, onChange, large, className, onBlur, ...props },
  ref,
) {
  return (
    <div
      className={cn(
        "flex items-center gap-2 rounded-lg border border-input bg-card px-3 focus-within:outline-2 focus-within:outline-offset-1 focus-within:outline-ring",
        large ? "min-h-16" : "min-h-touch",
        className,
      )}
    >
      <span className={cn("font-semibold text-muted-foreground", large ? "text-2xl" : "text-base")} aria-hidden="true">
        R$
      </span>
      <input
        ref={ref}
        inputMode="decimal"
        autoComplete="off"
        placeholder="0,00"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/[^\d.,]/g, ""))}
        onBlur={(e) => {
          const c = parseMoneyInput(value);
          if (c !== null) onChange(centsToInput(c));
          onBlur?.(e);
        }}
        className={cn(
          "money w-full min-w-0 bg-transparent font-bold text-foreground outline-none placeholder:text-muted-foreground",
          large ? "text-3xl" : "text-base",
        )}
        {...props}
      />
    </div>
  );
});
