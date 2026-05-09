import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { getMarketQuotes } from "../../api/marketApi";
import { useTheme } from "../../theme/ThemeContext";
import { formatNumber } from "../../utils/formatters";

const PRIORITY_SYMBOLS = [
  "XU100",
  "BIST100",
  "BTCUSDT",
  "BTCTRY",
  "BTC",
  "USDTRY",
  "EURTRY",
  "XAUTRY",
  "GRAMALTIN",
  "ETHUSDT",
  "ETHTRY",
  "ETH",
];

function getInitials(user) {
  const source = user?.fullName || user?.email || "FP";
  return source
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("");
}

export default function AppLayout() {
  const { i18n, t } = useTranslation();
  const { isAdmin, isAuthenticated, login, logout, register, user } = useAuth();
  const { setThemePreference, themePreference } = useTheme();
  const navigate = useNavigate();
  const [authPromptOpen, setAuthPromptOpen] = useState(false);
  const [tickerQuotes, setTickerQuotes] = useState([]);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef(null);
  const displayName = user?.fullName || user?.email || t("layout.guest");
  const profileLabel = isAuthenticated ? t("layout.connectedAccount") : t("layout.openAccess");

  const navGroups = useMemo(
    () => [
      {
        label: t("nav.mainMenu"),
        items: [
          { to: "/dashboard", label: t("nav.dashboard") },
          { to: "/markets", label: t("nav.markets") },
          { to: "/portfolio", label: t("nav.portfolio"), requiresAuth: true },
          { to: "/analysis", label: t("nav.analysis"), requiresAuth: true },
          { to: "/news", label: t("nav.news") },
          { to: "/alerts", label: t("nav.alerts"), requiresAuth: true },
          { to: "/simulation", label: t("nav.simulation"), requiresAuth: true },
          { to: "/reports", label: t("nav.reports"), requiresAuth: true },
          { to: "/profile", label: t("nav.profile"), requiresAuth: true },
        ],
      },
    ],
    [t],
  );

  useEffect(() => {
    let active = true;

    async function loadTicker() {
      try {
        const data = await getMarketQuotes();
        if (!active) {
          return;
        }
        setTickerQuotes(data ?? []);
      } catch {
        if (active) {
          setTickerQuotes([]);
        }
      }
    }

    loadTicker();

    return () => {
      active = false;
    };
  }, []);

  const tapeItems = useMemo(() => {
    if (!tickerQuotes.length) {
      return [];
    }

    const priorityMatches = PRIORITY_SYMBOLS.map((symbol) =>
      tickerQuotes.find((item) => item.symbol?.toUpperCase() === symbol),
    ).filter(Boolean);

    const fallback = [...tickerQuotes]
      .sort((left, right) => Math.abs(Number(right.changeRate) || 0) - Math.abs(Number(left.changeRate) || 0))
      .slice(0, 8);

    const merged = [...priorityMatches, ...fallback];
    const unique = [];
    const seen = new Set();

    merged.forEach((item) => {
      if (!item?.symbol || seen.has(item.symbol)) {
        return;
      }
      seen.add(item.symbol);
      unique.push(item);
    });

    return unique.slice(0, 10);
  }, [tickerQuotes]);

  const resolvedNavGroups = useMemo(() => {
    if (!isAdmin) {
      return navGroups;
    }

    return [
      {
        ...navGroups[0],
        items: [...navGroups[0].items, { to: "/admin", label: t("nav.admin"), requiresAuth: true }],
      },
    ];
  }, [isAdmin, navGroups, t]);

  useEffect(() => {
    function handlePointerDown(event) {
      if (!userMenuRef.current?.contains(event.target)) {
        setUserMenuOpen(false);
      }
    }

    function handleEscape(event) {
      if (event.key === "Escape") {
        setUserMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);

  function handleProtectedNavigation(event, item) {
    if (!item.requiresAuth || isAuthenticated) {
      return;
    }

    event.preventDefault();
    setAuthPromptOpen(true);
  }

  async function handleLoginClick() {
    setAuthPromptOpen(false);
    await login();
  }

  function handleAccountPrimaryAction() {
    if (isAuthenticated) {
      navigate("/profile");
      setUserMenuOpen(false);
      return;
    }

    setUserMenuOpen(false);
    handleLoginClick();
  }

  return (
    <>
      {tapeItems.length > 0 ? (
        <div className="market-tape-shell" aria-label={t("layout.liveTape")}>
          <div className="market-tape-track">
            {[...tapeItems, ...tapeItems].map((item, index) => (
              <div key={`${item.symbol}-${index}`} className="market-tape-item">
                <span className="market-tape-symbol">{item.code || item.symbol}</span>
                <strong>{formatNumber(item.price)}</strong>
                <span className={Number(item.changeRate) >= 0 ? "market-up" : "market-down"}>
                  {formatTapeChange(item.changeRate)}
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      <div className="app-shell">
        <aside className="app-sidebar">
          <div className="brand-block">
            <img className="brand-mark brand-logo-image" src="/finans-portali-logo.png" alt="Finans Portali" />
            <div>
              <h2>Finans Portal</h2>
            </div>
          </div>

          <div className="sidebar-profile">
            <div className="sidebar-profile-copy">
              <strong>{displayName}</strong>
              <p>{profileLabel}</p>
            </div>
            {isAuthenticated ? (
              <button type="button" className="ghost-button light sidebar-login-button" onClick={logout}>
                {t("layout.logout")}
              </button>
            ) : (
              <div className="auth-button-group">
                <button type="button" className="ghost-button light sidebar-login-button" onClick={login}>
                  {t("layout.login")}
                </button>
                <button type="button" className="sidebar-register-button" onClick={register}>
                  {t("layout.register")}
                </button>
              </div>
            )}
          </div>

          <nav className="sidebar-nav">
            {resolvedNavGroups.map((group) => (
              <div key={group.label} className="nav-group">
                <p className="nav-group-title">{group.label}</p>
                {group.items.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === "/"}
                    onClick={(event) => handleProtectedNavigation(event, item)}
                    className={({ isActive }) => `nav-item${isActive ? " active" : ""}`}
                  >
                    <span>{item.label}</span>
                    {item.requiresAuth && !isAuthenticated ? (
                      <small
                        className="nav-auth-indicator"
                        title={t("layout.authRequiredTitle")}
                        aria-label={t("layout.authRequiredTitle")}
                      >
                        🔒
                      </small>
                    ) : null}
                    {item.badge ? <small>{item.badge}</small> : null}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>

          <div className="sidebar-footnote">
            <span className="live-dot" />
            <p>{t("layout.footnote")}</p>
          </div>
        </aside>

        <div className="app-main">
          <header className="topbar">
            <div className="topbar-actions">
              <div className="topbar-status-pill">
                <span className="live-dot" />
                <strong>{t("layout.liveFeed")}</strong>
              </div>

              <div className="theme-switcher theme-switcher-inline" role="group" aria-label={t("common.language")}>
                <div className="theme-switcher-options">
                  {[
                    { value: "tr", label: t("common.turkish") },
                    { value: "en", label: t("common.english") },
                  ].map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`theme-switcher-button${i18n.resolvedLanguage === option.value ? " active" : ""}`}
                      onClick={() => i18n.changeLanguage(option.value)}
                      aria-pressed={i18n.resolvedLanguage === option.value}
                      title={option.label}
                    >
                      {option.value.toUpperCase()}
                    </button>
                  ))}
                </div>
              </div>

              <button type="button" className="icon-button" aria-label={t("layout.notifications")}>
                1
              </button>

              <div className="topbar-user-shell" ref={userMenuRef}>
                <button
                  type="button"
                  className="topbar-user topbar-user-button"
                  onClick={() => setUserMenuOpen((current) => !current)}
                  aria-haspopup="menu"
                  aria-expanded={userMenuOpen}
                >
                  <div className="topbar-user-copy">
                    <strong>{displayName}</strong>
                    <span>{isAuthenticated ? t("layout.analystMode") : t("layout.guestMode")}</span>
                  </div>
                  <div className="profile-avatar small">{getInitials(user)}</div>
                </button>

                {userMenuOpen ? (
                  <div className="topbar-user-menu" role="menu" aria-label={t("layout.userMenu")}>
                    {isAuthenticated ? (
                      <>
                        <button type="button" className="topbar-user-menu-item" onClick={handleAccountPrimaryAction}>
                          {t("layout.myProfile")}
                        </button>
                        <button
                          type="button"
                          className="topbar-user-menu-item"
                          onClick={() => {
                            navigate("/reports");
                            setUserMenuOpen(false);
                          }}
                        >
                          {t("nav.reports")}
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="topbar-user-menu-item"
                          onClick={() => {
                            login();
                            setUserMenuOpen(false);
                          }}
                        >
                          {t("layout.login")}
                        </button>
                        <button
                          type="button"
                          className="topbar-user-menu-item"
                          onClick={() => {
                            register();
                            setUserMenuOpen(false);
                          }}
                        >
                          {t("layout.register")}
                        </button>
                      </>
                    )}

                    <div className="topbar-user-menu-section">
                      <span className="topbar-user-menu-label">{t("layout.theme")}</span>
                      <div className="theme-switcher theme-switcher-in-menu" role="group" aria-label={t("layout.themeSelection")}>
                        <div className="theme-switcher-options">
                          {[
                            { value: "light", label: t("layout.themeLight") },
                            { value: "dark", label: t("layout.themeDark") },
                            { value: "system", label: t("layout.themeSystem") },
                          ].map((option) => (
                            <button
                              key={option.value}
                              type="button"
                              className={`theme-switcher-button${themePreference === option.value ? " active" : ""}`}
                              onClick={() => setThemePreference(option.value)}
                              aria-pressed={themePreference === option.value}
                              title={option.label}
                            >
                              {option.label}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>

                    {isAuthenticated ? (
                      <button
                        type="button"
                        className="topbar-user-menu-item danger"
                        onClick={() => {
                          logout();
                          setUserMenuOpen(false);
                        }}
                      >
                        {t("layout.logout")}
                      </button>
                    ) : null}
                  </div>
                ) : null}
              </div>
            </div>
          </header>

          <main className="page-content">
            <Outlet />
          </main>
        </div>
      </div>

      {authPromptOpen ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setAuthPromptOpen(false)}>
          <div
            className="auth-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-required-title"
            onClick={(event) => event.stopPropagation()}
          >
            <p className="eyebrow">{t("layout.authRequired")}</p>
            <h3 id="auth-required-title">{t("layout.authRequiredTitle")}</h3>
            <p className="auth-modal-copy">{t("layout.authRequiredDescription")}</p>
            <div className="actions-row">
              <button type="button" className="secondary-button" onClick={() => setAuthPromptOpen(false)}>
                {t("common.cancel")}
              </button>
              <button type="button" onClick={handleLoginClick}>
                {t("layout.login")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}

function formatTapeChange(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }

  const numeric = Number(value);
  if (Number.isNaN(numeric)) {
    return String(value);
  }

  return `${numeric >= 0 ? "+" : ""}${numeric.toFixed(2)}%`;
}
