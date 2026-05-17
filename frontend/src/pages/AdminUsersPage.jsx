import AdminAuditLogsSection from "../components/admin/AdminAuditLogsSection";
import AdminPremiumSection from "../components/admin/AdminPremiumSection";
import UserManagementSection from "../components/admin/UserManagementSection";

export default function AdminUsersPage() {
  return (
    <div className="dashboard-stack admin-console-shell">
      <UserManagementSection />
      <AdminPremiumSection />
      <AdminAuditLogsSection />
    </div>
  );
}
