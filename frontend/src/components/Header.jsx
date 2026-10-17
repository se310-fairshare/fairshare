import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import FairShareLogo from '../resources/FairShareLogo.png';

// #83: the profile button from the redesign mockup, a person outline in a light circle.
function ProfileIcon() {
  return (
    <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor"
         strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20v-1.5A4.5 4.5 0 0 1 9.5 14h5a4.5 4.5 0 0 1 4.5 4.5V20" />
    </svg>
  );
}

function MenuIcon({ open }) {
  return (
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor"
         strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      {open
        ? <path d="M6 6l12 12M18 6L6 18" />
        : <path d="M4 7h16M4 12h16M4 17h16" />}
    </svg>
  );
}

// #84: the app's main sections, reachable from every signed-in screen.
const NAV_LINKS = [
  { to: '/groups', label: 'Groups' },
  { to: '/debts', label: 'Individual debts' },
  { to: '/spending', label: 'My spending' },
];

// Log In and Register are for signed-out visitors, so they get no navigation.
const SIGNED_OUT_PATHS = new Set(['/login', '/register']);

function Header() {
  const { pathname } = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  // Log In shows the large logo in the page itself, so the header only adds it elsewhere.
  const showsLargeLogo = pathname === '/login';
  const showsNav = !SIGNED_OUT_PATHS.has(pathname);

  // AC5: the phone menu closes once the user moves to another screen.
  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!menuOpen) return undefined;
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [menuOpen]);

  return (
    <header className="header">
      <div className="header-inner">
        {!showsLargeLogo && (
          <Link to="/" className="logo" aria-label="FairShare home">
            <img src={FairShareLogo} alt="FairShare logo" className="logo-image" />
          </Link>
        )}

        {showsNav && (
          <>
            {/* AC5: on a phone the links collapse behind this button (see .header-nav in index.css). */}
            <button
              type="button"
              className="menu-button"
              aria-expanded={menuOpen}
              aria-controls="main-nav"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              onClick={() => setMenuOpen((open) => !open)}
            >
              <MenuIcon open={menuOpen} />
            </button>

            <nav id="main-nav" aria-label="Main" className={`header-nav${menuOpen ? ' header-nav-open' : ''}`}>
              {NAV_LINKS.map(({ to, label }) => (
                <NavLink key={to} to={to} className="nav-link" onClick={() => setMenuOpen(false)}>
                  {label}
                </NavLink>
              ))}
              <NavLink to="/profile" className="nav-link nav-link-profile" onClick={() => setMenuOpen(false)}>
                Profile
              </NavLink>
              <Link to="/profile" className="profile-button" aria-label="My profile">
                <ProfileIcon />
              </Link>
            </nav>
          </>
        )}
      </div>
    </header>
  );
}

export default Header;
