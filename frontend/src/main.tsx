import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "sonner";
import "@fontsource/space-grotesk/400.css";
import "@fontsource/space-grotesk/500.css";
import "@fontsource/space-grotesk/600.css";
import "@fontsource/space-grotesk/700.css";
import "@fontsource/montserrat/600.css";
import { ThemeProvider, useTheme } from "@/theme/theme";
import { MonthProvider } from "@/lib/month";
import { ApiError, onUnauthorized } from "@/lib/api";
import { QuickAddProvider } from "@/components/QuickAdd";
import App from "@/App";
import "@/index.css";

const queryClient = new QueryClient({
  queryCache: new QueryCache(),
  mutationCache: new MutationCache(),
  defaultOptions: {
    queries: {
      retry: (count, e) => !(e instanceof ApiError && e.status >= 400 && e.status < 500) && count < 2,
      refetchOnWindowFocus: false,
      staleTime: 15_000,
    },
  },
});

// Sessão expirada: volta para a tela de entrar.
onUnauthorized(() => queryClient.setQueryData(["me"], null));

function ThemedToaster() {
  const { resolved } = useTheme();
  return (
    <Toaster
      theme={resolved}
      position="top-center"
      closeButton
      toastOptions={{
        classNames: {
          toast: "!bg-popover !text-popover-foreground !border-border !font-sans",
          actionButton: "!bg-primary !text-primary-foreground !font-semibold",
        },
      }}
    />
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <MonthProvider>
            <QuickAddProvider>
              <App />
            </QuickAddProvider>
          </MonthProvider>
        </BrowserRouter>
        <ThemedToaster />
      </QueryClientProvider>
    </ThemeProvider>
  </StrictMode>,
);
