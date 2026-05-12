import AdminAuditLogsSection from "../components/admin/AdminAuditLogsSection";
import BroadcastNotificationCard from "../components/admin/BroadcastNotificationCard";
import UserManagementSection from "../components/admin/UserManagementSection";

export default function AdminUsersPage() {
  return (
    <div className="dashboard-stack admin-console-shell">
      <UserManagementSection />
      <BroadcastNotificationCard />
      <AdminAuditLogsSection />
    </div>
  );
}
