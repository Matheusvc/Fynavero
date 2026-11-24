import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

const TONES = {
  neutral: "bg-muted text-muted-foreground",
  forecast: "bg-forecast-subtle text-forecast border border-dashed border-forecast",
  positive: "bg-positive-subtle text-positive",
  negative: "bg-negative-subtle text-negative",
  brand: "bg-accent text-accent-foreground",
} as const;

export function Badge({
  children,
  tone = "neutral",
  className,
}: {
  children: ReactNode;
  tone?: keyof typeof TONES;
  className?: string;
}) {
  return (
    <span className={cn("inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-medium", TONES[tone], className)}>
      {children}
    </span>
  );
}