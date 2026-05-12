import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { getMarketQuotes, getMarketTapeConfig } from "../../api/marketApi";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from "../../api/notificationApi";
import { extractErrorMessage } from "../../api/responseUtils";
import { useTheme } from "../../theme/ThemeContext";
import { formatDateTime, formatNumber } from "../../utils/formatters";
import {
  LayoutDashboard,
  LineChart,
  Briefcase,
  Newspaper,
  Bell,
  User,
  Shield,
} from "lucide-react";

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

function getNotificationAudienceLabel(notification, t) {
  return notification?.targetType === "BROADCAST"
    ? t("layout.notificationAudienceBroadcast")
    : t("layout.notificationAudiencePersonal");
}

function getNotificationTypeLabel(notification, t) {
  return t(`layout.notificationTypes.${notification?.type ?? "SYSTEM"}`);
}

export default function AppLayout() {
  const { i18n, t } = useTranslation();
  const { isAdmin, isAuthenticated, login, logout, register, role, user } = useAuth();
  const { setThemePreference, themePreference } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [authPromptOpen, setAuthPromptOpen] = useState(false);
  const [tickerQuotes, setTickerQuotes] = useState([]);
  const [marketTapeSymbols, setMarketTapeSymbols] = useState(PRIORITY_SYMBOLS);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [notificationMenuOpen, setNotificationMenuOpen] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState("");
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const [selectedNotification, setSelectedNotification] = useState(null);
  const [showWelcomeBanner, setShowWelcomeBanner] = useState(false);
  const userMenuRef = useRef(null);
  const notificationMenuRef = useRef(null);
  const displayName = user?.fullName || user?.email || t("layout.guest");
  const profileLabel = isAuthenticated ? t(`layout.roleLabels.${role || "USER"}`) : t("layout.openAccess");
  const showAdminTopbarLabel = isAdmin && !location.pathname.startsWith("/admin");

  const navigationItems = [
    {
      to: "/dashboard",
      label: t("nav.dashboard"),
      icon: LayoutDashboard,
    },
    {
      to: "/markets",
      label: t("nav.markets"),
      icon: LineChart,
    },
    {
      to: "/portfolio",
      label: t("nav.portfolio"),
      icon: Briefcase,
      requiresAuth: true,
    },
    {
      to: "/analysis",
      label: t("nav.analysis"),
      icon: LineChart,
      requiresAuth: true,
    },
    {
      to: "/news",
      label: t("nav.news"),
      icon: Newspaper,
    },
    {
      to: "/alerts",
      label: t("nav.alerts"),
      icon: Bell,
      requiresAuth: true,
    },
    {
      to: "/simulation",
      label: t("nav.simulation"),
      icon: LineChart,
      requiresAuth: true,
    },
    {
      to: "/reports",
      label: t("nav.reports"),
      icon: Briefcase,
      requiresAuth: true,
    },
    {
      to: "/profile",
      label: t("nav.profile"),
      icon: User,
      requiresAuth: true,
    },
    ...(isAdmin
      ? [
          {
            to: "/admin",
            label: t("nav.admin"),
            icon: Shield,
            requiresAuth: true,
          },
        ]
      : []),
  ];

  useEffect(() => {
    let active = true;

    async function loadTickerState() {
      try {
        const [data, symbols] = await Promise.all([
          getMarketQuotes(),
          getMarketTapeConfig().catch(() => PRIORITY_SYMBOLS),
        ]);
        if (!active) {
          return;
        }
        setTickerQuotes(data ?? []);
        setMarketTapeSymbols(Array.isArray(symbols) && symbols.length > 0 ? symbols : PRIORITY_SYMBOLS);
      } catch {
        if (active) {
          setTickerQuotes([]);
          setMarketTapeSymbols(PRIORITY_SYMBOLS);
        }
      }
    }

    loadTickerState();

    const handleConfigUpdated = () => {
      loadTickerState();
    };

    window.addEventListener("market-tape-config-updated", handleConfigUpdated);

    return () => {
      active = false;
      window.removeEventListener("market-tape-config-updated", handleConfigUpdated);
    };
  }, []);

  const tapeItems = useMemo(() => {
    if (!tickerQuotes.length) {
      return [];
    }

    const configuredSymbols = marketTapeSymbols.length > 0 ? marketTapeSymbols : PRIORITY_SYMBOLS;

    const priorityMatches = configuredSymbols.map((symbol) =>
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
  }, [marketTapeSymbols, tickerQuotes]);

  useEffect(() => {
    if (!isAuthenticated) {
      setNotifications([]);
      setUnreadCount(0);
      setNotificationMenuOpen(false);
      setNotificationsError("");
      setSelectedNotification(null);
      setShowWelcomeBanner(false);
      return;
    }

    let active = true;

    async function loadUnreadCount() {
      try {
        const count = await getUnreadNotificationCount();
        if (active) {
          setUnreadCount(count);
        }
      } catch {
        if (active) {
          setUnreadCount(0);
        }
      }
    }

    loadUnreadCount();

    return () => {
      active = false;
    };
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated || !user) {
      setShowWelcomeBanner(false);
      return;
    }

    const identity = user.keycloakId || user.email || user.fullName;
    if (!identity) {
      return;
    }

    const welcomeKey = `welcome-banner-seen:${identity}`;
    const alreadySeen = window.sessionStorage.getItem(welcomeKey);
    if (alreadySeen === "1") {
      setShowWelcomeBanner(false);
      return;
    }

    window.sessionStorage.setItem(welcomeKey, "1");
    setShowWelcomeBanner(true);
  }, [isAuthenticated, user]);

  useEffect(() => {
    function handlePointerDown(event) {
      if (!userMenuRef.current?.contains(event.target)) {
        setUserMenuOpen(false);
      }
      if (!notificationMenuRef.current?.contains(event.target)) {
        setNotificationMenuOpen(false);
      }
    }

    function handleEscape(event) {
      if (event.key === "Escape") {
        setUserMenuOpen(false);
        setNotificationMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleEscape);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);

  useEffect(() => {
    if (!notificationMenuOpen || !isAuthenticated) {
      return;
    }

    let active = true;

    async function loadNotifications() {
      try {
        setNotificationsLoading(true);
        setNotificationsError("");
        const rows = await getNotifications();
        if (active) {
          setNotifications(rows);
        }
      } catch (error) {
        if (active) {
          setNotificationsError(extractErrorMessage(error, t("layout.notificationsLoadError")));
        }
      } finally {
        if (active) {
          setNotificationsLoading(false);
        }
      }
    }

    loadNotifications();

    return () => {
      active = false;
    };
  }, [isAuthenticated, notificationMenuOpen, t]);

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

  async function handleNotificationClick(notification) {
    if (!notification) {
      return;
    }

    setSelectedNotification(notification);

    if (notification.read) {
      return;
    }

    try {
      const updated = await markNotificationAsRead(notification.id);
      setNotifications((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setSelectedNotification(updated);
      setUnreadCount((current) => Math.max(0, current - 1));
    } catch (error) {
      setNotificationsError(extractErrorMessage(error, t("layout.notificationsUpdateError")));
    }
  }

  async function handleMarkAllNotificationsAsRead() {
    try {
      setMarkingAllRead(true);
      setNotificationsError("");
      await markAllNotificationsAsRead();
      setNotifications((current) => current.map((item) => ({ ...item, read: true })));
      setUnreadCount(0);
    } catch (error) {
      setNotificationsError(extractErrorMessage(error, t("layout.notificationsUpdateError")));
    } finally {
      setMarkingAllRead(false);
    }
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
              <button type="button" className="ghost-button light sidebar-login-button sidebar-logout-button" onClick={logout}>
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
            {navigationItems.map((item) => {
              const Icon = item.icon;

              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}
                  onClick={(event) => handleProtectedNavigation(event, item)}
                >
                  <span className="nav-item-main">
                    <Icon size={18} aria-hidden="true" />
                    <span>{item.label}</span>
                  </span>
                </NavLink>
              );
            })}
          </nav>

          <div className="sidebar-footnote">
            <span className="live-dot" />
            <p>{t("layout.footnote")}</p>
          </div>
        </aside>

        <div className="app-main">
          <header className="topbar">
            {showAdminTopbarLabel ? (
              <div className="topbar-page-label">
                <strong>{t("nav.admin")}</strong>
              </div>
            ) : null}
            <div className="topbar-actions">

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

              <div className="topbar-user-shell" ref={notificationMenuRef}>
                <button
                  type="button"
                  className="icon-button notification-trigger"
                  aria-label={t("layout.notifications")}
                  onClick={() => {
                    if (!isAuthenticated) {
                      handleLoginClick();
                      return;
                    }
                    setNotificationMenuOpen((current) => !current);
                    setUserMenuOpen(false);
                  }}
                >
                  <span className="notification-trigger-icon" aria-hidden="true">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" focusable="false" aria-hidden="true">
                      <path d="M20 6H4C3.44772 6 3 6.44772 3 7V17C3 17.5523 3.44772 18 4 18H20C20.5523 18 21 17.5523 21 17V7C21 6.44772 20.5523 6 20 6Z" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/>
                      <path d="M3 7L12 13L21 7" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  </span>
                  {isAuthenticated && unreadCount > 0 ? (
                    <span className="notification-badge">{unreadCount > 99 ? "99+" : unreadCount}</span>
                  ) : null}
                </button>

                {notificationMenuOpen ? (
                  <div className="topbar-user-menu notification-menu" role="menu" aria-label={t("layout.notifications")}>
                    <div className="notification-menu-head">
                      <div>
                        <span className="topbar-user-menu-label">{t("layout.notifications")}</span>
                        <strong>{t("layout.notificationsSummary", { count: unreadCount })}</strong>
                      </div>
                      <button
                        type="button"
                        className="topbar-user-menu-item notification-menu-action"
                        onClick={handleMarkAllNotificationsAsRead}
                        disabled={markingAllRead || notifications.length === 0}
                      >
                        {markingAllRead ? t("layout.notificationsMarkingAll") : t("layout.notificationsMarkAll")}
                      </button>
                    </div>

                    {notificationsLoading ? (
                      <div className="status-box loading">{t("layout.notificationsLoading")}</div>
                    ) : notificationsError ? (
                      <div className="status-box error">{notificationsError}</div>
                    ) : notifications.length === 0 ? (
                      <div className="status-box empty">
                        <strong>{t("layout.notificationsEmptyTitle")}</strong>
                        <p>{t("layout.notificationsEmptyDescription")}</p>
                      </div>
                    ) : (
                      <div className="notification-menu-list">
                        {notifications.map((notification) => (
                          <button
                            key={notification.id}
                            type="button"
                            className={`notification-menu-item${notification.read ? "" : " unread"}`}
                            onClick={() => handleNotificationClick(notification)}
                          >
                            <div className="notification-menu-item-top">
                              <strong>{notification.title}</strong>
                              <div className="notification-menu-badges">
                                <span className={`notification-menu-badge${notification.targetType === "BROADCAST" ? " broadcast" : " personal"}`}>
                                  {getNotificationAudienceLabel(notification, t)}
                                </span>
                                <span className="notification-menu-type">{getNotificationTypeLabel(notification, t)}</span>
                              </div>
                            </div>
                            <p>{notification.message}</p>
                            <div className="notification-menu-item-meta">
                              <span>{formatDateTime(notification.createdAt)}</span>
                              <span>{notification.read ? t("layout.notificationRead") : t("layout.notificationUnread")}</span>
                            </div>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                ) : null}
              </div>

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
                    <span>{isAuthenticated ? t(`layout.roleLabels.${role || "USER"}`) : t("layout.guestMode")}</span>
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
            {showWelcomeBanner ? (
              <section className="welcome-banner panel-surface" aria-live="polite">
                <div className="welcome-banner-copy">
                  <span className="eyebrow">{t("layout.connectedAccount")}</span>
                  <strong>{t("layout.welcomeUser", { name: displayName })}</strong>
                </div>
                <button
                  type="button"
                  className="secondary-button welcome-banner-close"
                  onClick={() => setShowWelcomeBanner(false)}
                >
                  {t("common.close")}
                </button>
              </section>
            ) : null}
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

      {selectedNotification ? (
        <div className="modal-backdrop" role="presentation" onClick={() => setSelectedNotification(null)}>
          <div
            className="auth-modal notification-detail-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notification-detail-title"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="notification-detail-head">
              <div>
                <p className="eyebrow">{t("layout.notifications")}</p>
                <h3 id="notification-detail-title">{selectedNotification.title}</h3>
              </div>
              <button type="button" className="secondary-button" onClick={() => setSelectedNotification(null)}>
                {t("common.close")}
              </button>
            </div>

            <div className="notification-detail-badges">
              <span className={`notification-menu-badge${selectedNotification.targetType === "BROADCAST" ? " broadcast" : " personal"}`}>
                {getNotificationAudienceLabel(selectedNotification, t)}
              </span>
              <span className="notification-menu-type">{getNotificationTypeLabel(selectedNotification, t)}</span>
              <span className={`notification-detail-state${selectedNotification.read ? " read" : " unread"}`}>
                {selectedNotification.read ? t("layout.notificationRead") : t("layout.notificationUnread")}
              </span>
            </div>

            <p className="notification-detail-message">{selectedNotification.message}</p>

            <div className="notification-detail-meta">
              <span>{t("layout.notificationDetailDate")}</span>
              <strong>{formatDateTime(selectedNotification.createdAt)}</strong>
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
