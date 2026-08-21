export const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateUserProfile({
  username,
  email,
  password,
  country,
  currency,
  requirePassword = true
}) {
  const newErrors = {};

  if (!email) {
    newErrors.email = 'Email is required';
  } else if (!emailRegex.test(email)) {
    newErrors.email = 'Email is invalid';
  }

  if (!username) {
    newErrors.username = 'Username is required';
  } else if (username.trim().length < 3) {
    newErrors.username = 'Username must be at least 3 characters';
  }

  if (requirePassword) {
    if (!password) {
      newErrors.password = 'Password is required';
    } else if (password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    }
  } else if (password && password.length < 8) {
    newErrors.password = 'Password must be at least 8 characters';
  }

  if (!country) {
    newErrors.country = 'Country is required';
  }

  if (!currency) {
    newErrors.currency = 'Currency is required';
  }

  return newErrors;
}
