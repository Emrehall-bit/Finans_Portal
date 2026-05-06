import { Navigate } from "react-router-dom";
import { useAuth } from "./AuthContext";

export default function EntryRedirect() {
  const { initialized } = useAuth();

  if (!initialized) {
    return <div className="page-shell">Yukleniyor...</div>;
  }

  return <Navigate to="/dashboard" replace />;
}
