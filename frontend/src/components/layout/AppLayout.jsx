import { NavLink, Outlet } from "react-router-dom";

const navItems = [
  { to: "/", label: "Dashboard" },
  { to: "/markets", label: "Markets" },
  { to: "/news", label: "News" },
  { to: "/portfolio", label: "Portfolio" },
  { to: "/watchlist", label: "Watchlist" },
  { to: "/alerts", label: "Alerts" },
];

export default function AppLayout() {
  return (
    <div className="layout">
      <aside className="sidebar">
        <h2>Finans Portal</h2>
        <nav>
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === "/"}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
