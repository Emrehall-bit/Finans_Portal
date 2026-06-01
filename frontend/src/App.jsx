import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/layout/AppLayout";
import AdminRoute from "./auth/AdminRoute";
import EntryRedirect from "./auth/EntryRedirect";
import ProtectedRoute from "./auth/ProtectedRoute";

const AlertsPage = lazy(() => import("./pages/AlertsPage"));
const AnalysisPage = lazy(() => import("./pages/AnalysisPage"));
const AdminDataPage = lazy(() => import("./pages/AdminDataPage"));
const AdminUsersPage = lazy(() => import("./pages/AdminUsersPage"));
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const EconomyPage = lazy(() => import("./pages/EconomyPage"));
const LoginPage = lazy(() => import("./pages/LoginPage"));
const MarketDetailPage = lazy(() => import("./pages/MarketDetailPage"));
const MarketsPage = lazy(() => import("./pages/MarketsPage"));
const NewsDetailPage = lazy(() => import("./pages/NewsDetailPage"));
const NewsPage = lazy(() => import("./pages/NewsPage"));
const PortfolioDetailPage = lazy(() => import("./pages/PortfolioDetailPage"));
const PortfolioPage = lazy(() => import("./pages/PortfolioPage"));
const ProfilePage = lazy(() => import("./pages/ProfilePage"));
const SimulationPage = lazy(() => import("./pages/SimulationPage"));

function App() {
  return (
    <Suspense fallback={null}>
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
            <Route element={<AdminRoute />}>
              <Route path="/admin" element={<Navigate to="/admin/data" replace />} />
              <Route path="/admin/data" element={<AdminDataPage />} />
              <Route path="/admin/users" element={<AdminUsersPage />} />
            </Route>
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}

export default App;
