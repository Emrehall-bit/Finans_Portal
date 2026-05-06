import { Navigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import HomePage from "./HomePage";

export default function LoginPage() {
  const { hasAuthenticatedSession } = useAuth();

  if (hasAuthenticatedSession) {
    return <Navigate to="/dashboard" replace />;
  }

  return <HomePage />;
}
