import { useEffect, useMemo, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { getMarketQuotes } from "../../api/marketApi";
import { formatNumber } from "../../utils/formatters";

const navGroups = [
  {
    label: "Genel Bakis",
    items: [
      { to: "/", label: "Ana Sayfa", badge: "Live" },
      { to: "/markets", label: "Piyasa Verileri", badge: "Feed" },
      { to: "/dashboard", label: "Dashboard", badge: "Pro" },
      { to: "/news", label: "Haber Akisi" },
    ],
  },
  {
    label: "Portfoy",
    items: [
      { to: "/profile", label: "Profilim", requiresAuth: true },
      { to: "/portfolio", label: "Portfoy", requiresAuth: true },
      { to: "/watchlist", label: "Watchlist", requiresAuth: true },
      { to: "/alerts", label: "Alerts", requiresAuth: true },
    ],
  },
];

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
  const { isAuthenticated, login, logout, user } = useAuth();
  const [authPromptOpen, setAuthPromptOpen] = useState(false);
  const [tickerQuotes, setTickerQuotes] = useState([]);
  const displayName = user?.fullName || user?.email || "Misafir";
  const profileLabel = isAuthenticated ? "Bagli hesap" : "Acik erisim";

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

  return (
    <>
      {tapeItems.length > 0 ? (
        <div className="market-tape-shell" aria-label="Canli piyasa bandi">
          <div className="market-tape-track">
            {[...tapeItems, ...tapeItems].map((item, index) => (
              <div key={`${item.symbol}-${index}`} className="market-tape-item">
                <span className="market-tape-symbol">{item.symbol}</span>
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
            <div className="brand-mark">F</div>
            <div>
              <p className="eyebrow">Market Terminal</p>
              <h2>Finans Portal</h2>
            </div>
          </div>

          <div className="sidebar-profile">
            <div className="profile-avatar">{getInitials(user)}</div>
            <div>
              <strong>{displayName}</strong>
              <p>{profileLabel}</p>
            </div>
          </div>

          <div className="sidebar-action-card">
            <p>{isAuthenticated ? "Verileriniz ve kisisel listeleriniz eszamanli." : "Acik modda market ve haber ekranlari hazir."}</p>
            <button type="button" className="ghost-button light" onClick={isAuthenticated ? logout : handleLoginClick}>
              {isAuthenticated ? "Cikis Yap" : "Giris Yap"}
            </button>
          </div>

          <nav className="sidebar-nav">
            {navGroups.map((group) => (
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
                    {item.requiresAuth && !isAuthenticated ? <small>Auth</small> : null}
                    {item.badge ? <small>{item.badge}</small> : null}
                  </NavLink>
                ))}
              </div>
            ))}
          </nav>

          <div className="sidebar-footnote">
            <span className="live-dot" />
            <p>Piyasa, haber ve teknik analiz ayni shell icinde.</p>
          </div>
        </aside>

        <div className="app-main">
          <header className="topbar">
            <div className="topbar-search">
              <span className="search-icon">+</span>
              <input placeholder="Enstruman, haber veya sembol ara..." />
            </div>

            <div className="topbar-actions">
              <div className="topbar-status-pill">
                <span className="live-dot" />
                <strong>Canli feed</strong>
              </div>
              <button type="button" className="icon-button" aria-label="Notifications">
                1
              </button>
              <div className="topbar-user">
                <div className="topbar-user-copy">
                  <strong>{displayName}</strong>
                  <span>{isAuthenticated ? "Analist modu" : "Misafir modu"}</span>
                </div>
                <div className="profile-avatar small">{getInitials(user)}</div>
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
            <p className="eyebrow">Yetki Gerekiyor</p>
            <h3 id="auth-required-title">Goruntuleyebilmek icin giris yapmaniz gerekmektedir.</h3>
            <p className="auth-modal-copy">
              Bu bolum kullaniciya ozel veriler icerir. Devam etmek icin once hesabiniza giris yapin.
            </p>
            <div className="actions-row">
              <button type="button" className="secondary-button" onClick={() => setAuthPromptOpen(false)}>
                Vazgec
              </button>
              <button type="button" onClick={handleLoginClick}>
                Giris Yap
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
