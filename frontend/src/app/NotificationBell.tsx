import { useState } from "react";
import { Link } from "react-router";
import { Popover } from "radix-ui";
import { toast } from "sonner";
import { Bell, CalendarClock, CircleAlert, CircleCheck, Gauge } from "lucide-react";
import { Button } from "@/components/ui/button";
import { errorMessage } from "@/lib/api";
import { useNotifications, useTxMutations } from "@/lib/queries";
import type { AppNotification } from "@/lib/types";
import { cn } from "@/lib/utils";

function NotificationIcon({ n }: { n: AppNotification }) {
  const urgent = n.type === "CONTA_VENCIDA" || n.type === "LIMITE_ESTOURADO";
  const I = n.type.startsWith("LIMITE") ? Gauge : urgent ? CircleAlert : CalendarClock;
  return (
    <span
      className={cn(
        "grid size-8 shrink-0 place-items-center rounded-full",
        urgent ? "bg-negative-subtle text-negative" : "bg-accent text-accent-foreground",
      )}
      aria-hidden="true"
    >
      <I className="size-4" />
    </span>
  );
}

/** Sino do cabeçalho: contas vencidas, de hoje e de amanhã (com "Marcar como paga") e limites perto do fim. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const notifications = useNotifications();
  const { markPaid } = useTxMutations();
  const list = notifications.data ?? [];
  const urgent = list.some((n) => n.type === "CONTA_VENCIDA" || n.type === "CONTA_HOJE" || n.type === "LIMITE_ESTOURADO");

  return (
    <Popover.Root open={open} onOpenChange={setOpen}>
      <Popover.Trigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={list.length ? `Avisos: ${list.length} novos` : "Avisos"}>
          <Bell />
          {list.length > 0 && (
            <span
              className={cn(
                "absolute top-1.5 right-1.5 grid min-w-4.5 place-items-center rounded-full px-1 text-[0.625rem] leading-4.5 font-bold",
                urgent ? "bg-negative text-negative-foreground" : "bg-primary text-primary-foreground",
              )}
              aria-hidden="true"
            >
              {list.length > 9 ? "9+" : list.length}
            </span>
          )}
        </Button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={8}
          collisionPadding={16}
          className="z-50 w-[min(24rem,calc(100vw-2rem))] rounded-xl border bg-popover text-popover-foreground shadow-lg"
        >
          <div className="flex items-center justify-between border-b px-4 py-3">
            <h2 className="font-semibold">Avisos</h2>
            <Link
              to="/configuracoes#notificacoes"
              onClick={() => setOpen(false)}
              className="text-sm text-primary-text underline-offset-4 hover:underline"
            >
              Avisos por e-mail
            </Link>
          </div>
          {list.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-8 text-center text-sm text-muted-foreground">
              <CircleCheck className="size-6 text-positive" aria-hidden="true" />
              Tudo em dia. Nenhuma conta para pagar agora e nenhum limite perto do fim.
            </div>
          ) : (
            <ul className="max-h-[min(28rem,70vh)] divide-y overflow-y-auto">
              {list.map((n) => (
                <li key={n.id} className="flex gap-3 px-4 py-3">
                  <NotificationIcon n={n} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{n.title}</p>
                    <p className="text-xs text-muted-foreground">{n.body}</p>
                    {n.transactionId && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="mt-2"
                        disabled={markPaid.isPending}
                        onClick={() =>
                          markPaid.mutate(n.transactionId!, {
                            onSuccess: () => toast.success("Marcada como paga"),
                            onError: (e) => toast.error(errorMessage(e)),
                          })
                        }
                      >
                        <CircleCheck /> Marcar como paga
                      </Button>
                    )}
                    {n.categoryId && (
                      <Link
                        to="/"
                        onClick={() => setOpen(false)}
                        className="mt-1 inline-block text-xs font-medium text-primary-text underline-offset-4 hover:underline"
                      >
                        Ver limites do mês
                      </Link>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
