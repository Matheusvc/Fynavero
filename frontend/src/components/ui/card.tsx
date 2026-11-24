import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function Card({
  className,
  children,
  as: As = "section",
  ...rest
}: {
  className?: string;
  children: ReactNode;
  as?: "section" | "div" | "article";
  "aria-labelledby"?: string;
}) {
  return (
    <As className={cn("rounded-xl border bg-card p-4 text-card-foreground shadow-sm sm:p-5", className)} {...rest}>
      {children}
    </As>
  );
}

export function CardTitle({ children, id, action }: { children: ReactNode; id?: string; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <h2 id={id} className="text-base font-semibold">
        {children}
      </h2>
      {action}
    </div>
  );
}