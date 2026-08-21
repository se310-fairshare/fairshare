import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { API_BASE } from '../api/config';

function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');

    const response = await fetch(`${API_BASE}/users/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify({ email, password })
    });

    if (!response.ok) {
      const message = await response.text();
      setError(message || 'Invalid email or password');
      return;
    }

    navigate('/');
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Log In</h1>
        <p className="subtitle">Welcome back to FairShare</p>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              placeholder="Enter your email"
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              placeholder="Enter your password"
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </div>

          {error && <div className="error">{error}</div>}

          <button type="submit">Log In</button>
        </form>

        <p className="subtitle" style={{ marginTop: '20px' }}>
          Need an account? <Link to="/register">Create profile</Link>
        </p>
      </div>
    </div>
  );
}

export default Login;
