export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

type Options = { method?: "GET" | "POST" | "PUT" | "DELETE"; body?: unknown; signal?: AbortSignal };

function readCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return m ? decodeURIComponent(m[1]!) : null;
}

async function refreshCsrf() {
  await fetch("/api/auth/csrf", { credentials: "same-origin" });
}

let unauthorizedHandler: (() => void) | null = null;
export function onUnauthorized(fn: () => void) {
  unauthorizedHandler = fn;
}

/** Cliente da API: sessão por cookie httpOnly + token CSRF no cabeçalho em toda alteração. */
export async function api<T>(path: string, { method = "GET", body, signal }: Options = {}): Promise<T> {
  const mutating = method !== "GET";
  if (mutating && !readCookie("XSRF-TOKEN")) await refreshCsrf();

  const send = () =>
    fetch(path, {
      method,
      credentials: "same-origin",
      signal,
      headers: {
        Accept: "application/json",
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(mutating ? { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") ?? "" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res: Response;
  try {
    res = await send();
    if (res.status === 403 && mutating) {
      await refreshCsrf();
      res = await send();
    }
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(0, "Sem conexão com o servidor. Confira sua internet e tente de novo.");
  }

  if (res.status === 204) return undefined as T;
  const data = res.headers.get("content-type")?.includes("json") ? await res.json().catch(() => null) : null;
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith("/api/auth/")) unauthorizedHandler?.();
    throw new ApiError(res.status, (data as { message?: string } | null)?.message ?? "Algo deu errado. Tente de novo.");
  }
  return data as T;
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : "Algo deu errado. Tente de novo.";
}
