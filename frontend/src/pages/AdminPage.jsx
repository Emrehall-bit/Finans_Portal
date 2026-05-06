import EmptyState from "../components/common/EmptyState";
import PageHeader from "../components/common/PageHeader";

export default function AdminPage() {
  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow="Admin"
        title="Admin Paneli"
        description="Yonetimsel is akislari, sistem senkronizasyonlari ve operator araclari."
      />
      <section className="panel-surface">
        <EmptyState
          title="Admin paneli hazirlaniyor"
          description="Yalnizca admin rolu olan kullanicilar bu ekrani gorebilir."
        />
      </section>
    </div>
  );
}
