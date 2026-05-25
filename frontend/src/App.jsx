import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import AlertsPage from "./pages/AlertsPage";
import AnalysisPage from "./pages/AnalysisPage";
import AdminDataPage from "./pages/AdminDataPage";
import AdminFinancialImportPage from "./pages/AdminFinancialImportPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import DashboardPage from "./pages/DashboardPage";
import EconomyPage from "./pages/EconomyPage";
import LoginPage from "./pages/LoginPage";
import MarketDetailPage from "./pages/MarketDetailPage";
import MarketsPage from "./pages/MarketsPage";
import NewsDetailPage from "./pages/NewsDetailPage";
import NewsPage from "./pages/NewsPage";
import PortfolioDetailPage from "./pages/PortfolioDetailPage";
import PortfolioPage from "./pages/PortfolioPage";
import ProfilePage from "./pages/ProfilePage";
import ReportsPage from "./pages/ReportsPage";
import SimulationPage from "./pages/SimulationPage";
import AdminRoute from "./auth/AdminRoute";
import EntryRedirect from "./auth/EntryRedirect";
import ProtectedRoute from "./auth/ProtectedRoute";

function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<EntryRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/markets" element={<MarketsPage />} />
        <Route path="/markets/:symbol" element={<MarketDetailPage />} />
        <Route path="/economy" element={<EconomyPage />} />
        <Route path="/news" element={<NewsPage />} />
        <Route path="/news/:id" element={<NewsDetailPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/portfolio" element={<PortfolioPage />} />
          <Route path="/portfolio/:portfolioId" element={<PortfolioDetailPage />} />
          <Route path="/analysis" element={<AnalysisPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/simulation" element={<SimulationPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<Navigate to="/admin/data" replace />} />
            <Route path="/admin/data" element={<AdminDataPage />} />
            <Route path="/admin/financial-import" element={<AdminFinancialImportPage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

export default App;
