import { useTranslation } from "react-i18next";

const BASE_TABS = ["overview", "chart"];
const STOCK_ONLY_TABS = ["kapDisclosures", "financials", "fundamentals"];

export default function InstrumentTabs({ activeTab, onChange, instrumentType }) {
  const { t } = useTranslation();
  const isStock = instrumentType === "STOCK";
  const tabs = isStock ? [...BASE_TABS, ...STOCK_ONLY_TABS] : BASE_TABS;

  return (
    <div className="instrument-detail-tabs" role="tablist" aria-label={t("instrumentDetail.tabs.ariaLabel")}>
      {tabs.map((tab) => (
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
