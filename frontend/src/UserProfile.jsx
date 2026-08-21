import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { validateUserProfile } from './utils/userValidation';
import {API_BASE} from "./api/config.js";

const countries = [
  { name: 'New Zealand', value: 'NEW_ZEALAND', currency: 'NZD' },
  { name: 'Australia', value: 'AUSTRALIA', currency: 'AUD' }
];

const currencies = [
  'NZD',
  'AUD'
];

function UserProfile() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [country, setCountry] = useState('');
  const [currency, setCurrency] = useState('');
  const [errors, setErrors] = useState({});
  const navigate = useNavigate();

  function handleCountryChange(event) {
    const selectedCountry = event.target.value;
    setCountry(selectedCountry);

    const countryData = countries.find(
      (c) => c.value === selectedCountry
    );

    if (countryData) {
      setCurrency(countryData.currency);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();

    const validationErrors = validateUserProfile({
      username,
      password,
      email,
      country,
      currency
    });
    setErrors(validationErrors);

    if (Object.keys(validationErrors).length === 0) {

      const user = {
        username: username,
        password: password,
        email: email,
        country: country,
        currency: currency
      };

      const response = await fetch(`${API_BASE}/users/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        credentials: 'include',
        body: JSON.stringify(user)
      });


      if (response.status === 409) {
        setErrors({
          email: 'Email is already registered'
        });
      } else {
        // On success, navigate back to the landing page
        navigate('/');
      }
    }
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Create Your Profile</h1>
        <p className="subtitle">Enter your information below</p>

        <form onSubmit={handleSubmit}>

          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              value={username}
              placeholder="Enter your username"
              onChange={(event) => setUsername(event.target.value)}
            />
            {errors.username && <span className="error">{errors.username}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              placeholder="Enter your password"
              onChange={(event) => setPassword(event.target.value)}
            />
            {errors.password && <span className="error">{errors.password}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              placeholder="Enter your email"
              onChange={(event) => setEmail(event.target.value)}
            />
            {errors.email && <span className="error">{errors.email}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="country">Country</label>
            <select id="country" value={country} onChange={handleCountryChange}>
              <option value="">Select your country</option>
              {countries.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.name}
                </option>
              ))}
            </select>
            {errors.country && <span className="error">{errors.country}</span>}
          </div>

          <div className="form-group">
            <label htmlFor="currency">Currency</label>
            <select
              id="currency"
              value={currency}
              onChange={(event) => setCurrency(event.target.value)}
            >
              <option value="">Select your currency</option>
              {currencies.map((curr) => (
                <option key={curr} value={curr}>
                  {curr}
                </option>
              ))}
            </select>
            {errors.currency && <span className="error">{errors.currency}</span>}
          </div>

          <button type="submit">Create Profile</button>
        </form>

      </div>
    </div>
  );
}

export default UserProfile;