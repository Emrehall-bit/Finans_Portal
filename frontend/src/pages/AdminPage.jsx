import { useTranslation } from "react-i18next";
import EmptyState from "../components/common/EmptyState";
import PageHeader from "../components/common/PageHeader";

export default function AdminPage() {
  const { t } = useTranslation();

  return (
    <div className="dashboard-stack">
      <PageHeader
        eyebrow={t("admin.eyebrow")}
        title={t("admin.title")}
        description={t("admin.description")}
      />
      <section className="panel-surface">
        <EmptyState
          title={t("admin.emptyTitle")}
          description={t("admin.emptyDescription")}
        />
      </section>
    </div>
  );
}
