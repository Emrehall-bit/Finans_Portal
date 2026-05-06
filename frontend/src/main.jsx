import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import { AuthProvider } from "./auth/AuthContext.jsx";
import { initKeycloak } from "./auth/keycloak.js";
import "./i18n";
import { ThemeProvider } from "./theme/ThemeContext.jsx";
import "./styles/global.css";

const root = createRoot(document.getElementById("root"));

async function bootstrapApplication() {
  try {
    await initKeycloak();
  } catch (error) {
    console.error("Keycloak bootstrap failed.", error);
  }

  root.render(
    <StrictMode>
      <ThemeProvider>
        <AuthProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </AuthProvider>
      </ThemeProvider>
    </StrictMode>,
  );
}

bootstrapApplication();
