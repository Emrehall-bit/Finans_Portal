const TABS = [
  { key: "overview", label: "Genel Bakis" },
  { key: "chart", label: "Grafik" },
  { key: "news", label: "Haberler" },
  { key: "financials", label: "Finansallar" },
];

export default function InstrumentTabs({ activeTab, onChange }) {
  return (
    <div className="instrument-detail-tabs" role="tablist" aria-label="Enstruman detay sekmeleri">
      {TABS.map((tab) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          className={`market-detail-tab ${activeTab === tab.key ? "active" : ""}`}
          aria-selected={activeTab === tab.key}
          onClick={() => onChange(tab.key)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
