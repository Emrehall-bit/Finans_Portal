import { useTranslation } from "react-i18next";

function FilterSection({ title, options, selectedValues, onToggle }) {
  if (!options || options.length === 0) {
    return null;
  }

  return (
    <section className="news-sidebar-section">
      <div className="news-sidebar-section-head">
        <h4>{title.toLocaleUpperCase("tr-TR")}</h4>
      </div>

      <div className="news-sidebar-checklist">
        {options.map((option) => {
          const checked = option.isAll ? selectedValues.length === 0 : selectedValues.includes(option.value);
          return (
            <button
              key={option.value}
              type="button"
              className={`news-sidebar-option${checked ? " is-selected" : ""}`}
              onClick={() => onToggle(option.value)}
              aria-pressed={checked}
            >
              <span className="news-sidebar-selection-indicator" aria-hidden="true" />
              <span className="news-sidebar-option-copy">{option.label ?? option.value}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}

function DateRangeSection({ title, dateFrom, dateTo, onDateFromChange, onDateToChange }) {
  return (
    <section className="news-sidebar-section">
      <div className="news-sidebar-section-head">
        <h4>{title.toLocaleUpperCase("tr-TR")}</h4>
      </div>
      <div className="news-sidebar-date-range">
        <label className="news-sidebar-date-field">
          <span className="news-sidebar-date-label">Başlangıç</span>
          <input
            type="date"
            className="news-sidebar-date-input"
            value={dateFrom}
            max={dateTo || undefined}
            onChange={(e) => onDateFromChange(e.target.value)}
          />
        </label>
        <label className="news-sidebar-date-field">
          <span className="news-sidebar-date-label">Bitiş</span>
          <input
            type="date"
            className="news-sidebar-date-input"
            value={dateTo}
            min={dateFrom || undefined}
            onChange={(e) => onDateToChange(e.target.value)}
          />
        </label>
      </div>
    </section>
  );
}

export default function NewsSidebarFilters({
  categoryOptions,
  providerOptions,
  languageOptions,
  selectedCategories,
  selectedProviders,
  selectedLanguages,
  dateFrom = "",
  dateTo = "",
  onToggleCategory,
  onToggleProvider,
  onToggleLanguage,
  onDateFromChange,
  onDateToChange,
  onReset,
  loading,
  compact = false,
}) {
  const { t } = useTranslation();

  return (
    <div className={`news-sidebar-card${compact ? " compact" : ""}`}>
      <div className="news-sidebar-copy">
        <h3>{t("news.filtersTitle")}</h3>
      </div>

      <FilterSection
        title={t("news.categories")}
        options={categoryOptions}
        selectedValues={selectedCategories}
        onToggle={onToggleCategory}
      />

      <FilterSection
        title={t("news.providers")}
        options={providerOptions}
        selectedValues={selectedProviders}
        onToggle={onToggleProvider}
      />

      <DateRangeSection
        title={t("news.dateRange")}
        dateFrom={dateFrom}
        dateTo={dateTo}
        onDateFromChange={onDateFromChange}
        onDateToChange={onDateToChange}
      />

      <FilterSection
        title={t("news.language")}
        options={languageOptions}
        selectedValues={selectedLanguages}
        onToggle={onToggleLanguage}
      />

      <div className="news-sidebar-actions">
        <button type="button" className="secondary-button" onClick={onReset} disabled={loading}>
          {t("common.clear")}
        </button>
      </div>
    </div>
  );
}
