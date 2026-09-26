import {
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useState } from "react";
import { useAuth } from "./auth/AuthContext";
import { Icon } from "./components/Icon";
import { SystemPage } from "./pages/SystemPage";
import { PrinterListPage } from "./pages/PrinterListPage";
import { PrinterFormPage } from "./pages/PrinterFormPage";
import { PrinterDetailsPage } from "./pages/PrinterDetailsPage";
import { RelocatePrinterPage } from "./pages/RelocatePrinterPage";
import { DashboardPage } from "./pages/DashboardPage";
import { LoginPage } from "./pages/LoginPage";
import { UserManagementPage } from "./pages/UserManagementPage";

export default function App() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, loading, logout } = useAuth();
  const [logoutError, setLogoutError] = useState("");

  if (loading) return <main className="auth-loading">Loading…</main>;
  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }
  if (pathname === "/login") return <Navigate to="/printers" replace />;

  async function signOut() {
    setLogoutError("");
    try {
      await logout();
      navigate("/login", { replace: true });
    } catch {
      setLogoutError("Logout could not be completed. Try again.");
    }
  }
  const section = pathname.startsWith("/users")
    ? "User Management"
    : pathname.startsWith("/system")
    ? "System overview"
    : pathname.startsWith("/printers")
      ? "Printers"
      : "Dashboard";
  return (
    <div className="app-shell">
      <a className="visually-hidden-focusable skip-link" href="#main-content">
        Skip to content
      </a>
      <aside className="sidebar">
        <img
          className="sidebar-logo"
          src="/ictlogo.png"
          alt="ICT Department logo"
        />
        <Link
          to="/"
          className="brand-lockup"
          aria-label="Printer Inventory home"
        >
          <span className="brand-icon">
            <Icon name="printer" />
          </span>
          <span>
            Printer Inventory<small>Organization workspace</small>
          </span>
        </Link>
        <div className="nav-label">WORKSPACE</div>
        <nav aria-label="Main navigation">
          <NavLink to="/dashboard" className="nav-item">
            <Icon name="grid" /> Dashboard
          </NavLink>
          <NavLink to="/printers" className="nav-item">
            <Icon name="printer" /> Printers
          </NavLink>
          <NavLink to="/system" className="nav-item">
            <Icon name="server" /> System overview
          </NavLink>
          {user.role === "ADMIN" && (
            <NavLink to="/users" className="nav-item">
              <Icon name="users" /> User Management
            </NavLink>
          )}
        </nav>
        <div className="sidebar-account">
          <div>
            <strong>{user.fullName}</strong>
            <span>{user.role}</span>
          </div>
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={signOut}>
            Logout
          </button>
          {logoutError && <small role="alert">{logoutError}</small>}
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span>
            Workspace <span className="breadcrumb-divider">/</span>{" "}
            <strong>{section}</strong>
          </span>
        </header>
        <main id="main-content" className="main-content">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/system" element={<SystemPage />} />
            <Route path="/users" element={user.role === "ADMIN" ? <UserManagementPage /> : <Navigate to="/printers" replace />} />
            <Route path="/printers" element={<PrinterListPage />} />
            <Route
              path="/printers/new"
              element={<PrinterFormPage key="new" />}
            />
            <Route
              path="/printers/:id/edit"
              element={<PrinterFormPage key={pathname} />}
            />
            <Route
              path="/printers/:id/relocate"
              element={<RelocatePrinterPage key={pathname} />}
            />
            <Route
              path="/printers/:id"
              element={<PrinterDetailsPage key={pathname} />}
            />
            <Route
              path="*"
              element={
                <div className="panel p-4">
                  <h1>Page not found</h1>
                  <p>This page is not available.</p>
                  <Link to="/">Return to printer inventory</Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="app-footer">
          All rights reserved. Copyright © 2026, Designed by ICT Department.
        </footer>
      </div>
    </div>
  );
}
