import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function AdminRoute() {
  const { initialized, isAdmin } = useAuth();

  if (!initialized) {
    return <div className="page-shell">Yetki kontrol ediliyor...</div>;
  }

  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return <Outlet />;
}
