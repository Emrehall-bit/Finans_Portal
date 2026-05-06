import { useTranslation } from "react-i18next";
import NewsSidebarFilters from "./NewsSidebarFilters";

export default function NewsFilterDrawer({ open, onClose, ...props }) {
  const { t } = useTranslation();

  if (!open) {
    return null;
  }

  function handleReset() {
    props.onReset();
  }

  return (
    <div className="news-filter-drawer" role="dialog" aria-modal="true" aria-label={t("news.drawerAria")}>
      <button type="button" className="news-filter-drawer-backdrop" onClick={onClose} aria-label={t("news.drawerCloseAria")} />

      <div className="news-filter-drawer-sheet">
        <div className="news-filter-drawer-handle" aria-hidden="true" />
        <div className="news-filter-drawer-head">
          <h3>{t("news.filtersTitle")}</h3>
          <button type="button" className="ghost-button" onClick={onClose}>
            {t("common.close")}
          </button>
        </div>

        <NewsSidebarFilters {...props} compact onReset={handleReset} />
      </div>
    </div>
  );
}
