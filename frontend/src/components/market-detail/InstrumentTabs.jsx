import { useTranslation } from "react-i18next";

const TABS = ["overview", "chart", "news", "financials", "fundamentals", "kapDisclosures"];

export default function InstrumentTabs({ activeTab, onChange }) {
  const { t } = useTranslation();

  return (
    <div className="instrument-detail-tabs" role="tablist" aria-label={t("instrumentDetail.tabs.ariaLabel")}>
      {TABS.map((tab) => (
        <button
          key={tab}
          type="button"
          role="tab"
          className={`market-detail-tab ${activeTab === tab ? "active" : ""}`}
          aria-selected={activeTab === tab}
          onClick={() => onChange(tab)}
        >
          {t(`instrumentDetail.tabs.${tab}`)}
        </button>
      ))}
    </div>
  );
}
