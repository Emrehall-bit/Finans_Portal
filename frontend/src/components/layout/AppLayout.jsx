import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const navGroups = [
  {
    label: "Overview",
    items: [
      { to: "/", label: "Ana Sayfa", badge: "Live" },
      { to: "/dashboard", label: "Dashboard", badge: "Pro" },
      { to: "/markets", label: "Piyasa Verileri" },
      { to: "/news", label: "Haber Akisi" },
    ],
  },
  {
    label: "Portfolio",
    items: [
      { to: "/portfolio", label: "Portfoy", requiresAuth: true },
      { to: "/watchlist", label: "Watchlist", requiresAuth: true },
      { to: "/alerts", label: "Alerts", requiresAuth: true },
    ],
  },
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
  const displayName = user?.fullName || user?.email || "Guest";
  const profileLabel = isAuthenticated ? "Connected account" : "Public access";

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
      <div className="app-shell">
        <aside className="app-sidebar">
          <div className="brand-block">
            <div className="brand-mark">F</div>
            <div>
              <p className="eyebrow">Finance Suite</p>
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
            <p>{isAuthenticated ? "Hesabiniz senkronize durumda." : "Public alanlari hemen kullanabilirsiniz."}</p>
            <button type="button" className="ghost-button light" onClick={isAuthenticated ? logout : handleLoginClick}>
              {isAuthenticated ? "Logout" : "Giris Yap"}
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
            <p>Piyasa ve haber modulleri tek panelde birlesiyor.</p>
          </div>
        </aside>

        <div className="app-main">
          <header className="topbar">
            <div className="topbar-search">
              <span className="search-icon">+</span>
              <input placeholder="Enstruman, haber veya sembol ara..." />
            </div>

            <div className="topbar-actions">
              <button type="button" className="icon-button" aria-label="Notifications">
                1
              </button>
              <div className="topbar-user">
                <div className="topbar-user-copy">
                  <strong>{displayName}</strong>
                  <span>{isAuthenticated ? "Analyst mode" : "Guest mode"}</span>
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
              Bu bolum kullaniciya ozel veriler icerir. Devam etmek icin once hesabinizla giris yapin.
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
