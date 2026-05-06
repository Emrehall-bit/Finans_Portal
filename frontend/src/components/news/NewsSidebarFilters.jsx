import { useTranslation } from "react-i18next";

function FilterSection({ title, options, selectedValues, onToggle, formatter = (value) => value }) {
  return (
    <section className="news-sidebar-section">
      <div className="news-sidebar-section-head">
        <h4>{title}</h4>
      </div>

      <div className="news-sidebar-checklist">
        {options.map((option) => {
          const checked = selectedValues.includes(option.value);
          return (
            <label key={option.value} className={`news-sidebar-option${checked ? " is-selected" : ""}`}>
              <input type="checkbox" checked={checked} onChange={() => onToggle(option.value)} />
              <span className="news-sidebar-checkbox" aria-hidden="true" />
              <span className="news-sidebar-option-copy">{formatter(option.label ?? option.value)}</span>
            </label>
          );
        })}
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
  onToggleCategory,
  onToggleProvider,
  onToggleLanguage,
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
