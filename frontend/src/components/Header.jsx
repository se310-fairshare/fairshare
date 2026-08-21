import { Link } from 'react-router-dom';
import FairShareLogo from '../resources/FairShareLogo.png';

function Header() {
  return (
    <header className="header">
      <div className="container header-inner">
        <Link to="/" className="logo" aria-label="FairShare home">
          <img src={FairShareLogo} alt="FairShare logo" className="logo-image" />
        </Link>
      </div>
    </header>
  );
}

export default Header;
