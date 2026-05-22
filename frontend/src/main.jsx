import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import App from "./App.jsx";
import { AuthProvider } from "./auth/AuthContext.jsx";
import { initKeycloak } from "./auth/keycloak.js";
import "./i18n";
import { ThemeProvider } from "./theme/ThemeContext.jsx";
import { CurrencyProvider } from "./currency/CurrencyContext.jsx";
import "./styles/global.css";
import "./styles/dashboard-theme.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      gcTime: 5 * 60_000,
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

const root = createRoot(document.getElementById("root"));

async function bootstrapApplication() {
  try {
    await initKeycloak();
  } catch (error) {
    console.error("Keycloak bootstrap failed.", error);
  }

  root.render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <CurrencyProvider>
            <AuthProvider>
              <BrowserRouter>
                <App />
              </BrowserRouter>
            </AuthProvider>
          </CurrencyProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
}

bootstrapApplication();
