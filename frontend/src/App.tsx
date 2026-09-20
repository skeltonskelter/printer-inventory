import {
  Link,
  NavLink,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import { Icon } from "./components/Icon";
import { SystemPage } from "./pages/SystemPage";
import { PrinterListPage } from "./pages/PrinterListPage";
import { PrinterFormPage } from "./pages/PrinterFormPage";
import { PrinterDetailsPage } from "./pages/PrinterDetailsPage";
import { RelocatePrinterPage } from "./pages/RelocatePrinterPage";
import { DashboardPage } from "./pages/DashboardPage";

export default function App() {
  const { pathname } = useLocation();
  const section = pathname.startsWith("/system")
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
        </nav>
        <div className="sidebar-note">
          <span className="phase-dot" /> Inventory workspace
          <p>A home for every printer.</p>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span>
            Workspace <span className="breadcrumb-divider">/</span>{" "}
            <strong>{section}</strong>
          </span>
          <span className="internal-label">Internal application</span>
        </header>
        <main id="main-content" className="main-content">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/system" element={<SystemPage />} />
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
          Printer Inventory <span>Organization workspace</span>
        </footer>
      </div>
    </div>
  );
}
