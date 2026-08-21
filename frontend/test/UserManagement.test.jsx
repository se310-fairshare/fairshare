import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import UserManagement from '../src/pages/UserManagement.jsx';
import { getCurrentUser, updateCurrentUser } from '../src/api/users';

vi.mock('../src/api/users', () => ({
  getCurrentUser: vi.fn(),
  updateCurrentUser: vi.fn(),
  logout: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

function renderPage() {
  render(
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route path="/profile" element={<UserManagement />} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
  );
}

it('validates profile updates using the same rules as account creation', async () => {
  const user = userEvent.setup();

  getCurrentUser.mockResolvedValue({
    id: 42,
    username: 'alice',
    email: 'alice@example.com',
    country: 'NEW_ZEALAND',
    currency: 'NZD'
  });

  renderPage();

  expect(await screen.findByText('Manage Profile')).toBeInTheDocument();

  await user.clear(screen.getByLabelText('Username'));
  await user.clear(screen.getByLabelText('Email'));
  await user.selectOptions(screen.getByLabelText('Country'), '');
  await user.selectOptions(screen.getByLabelText('Currency'), '');
  await user.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(await screen.findByText('Username is required')).toBeInTheDocument();
  expect(screen.getByText('Email is required')).toBeInTheDocument();
  expect(screen.getByText('Country is required')).toBeInTheDocument();
  expect(screen.getByText('Currency is required')).toBeInTheDocument();
  expect(updateCurrentUser).not.toHaveBeenCalled();
});

it('redirects to login when the user is not authenticated', async () => {
  getCurrentUser.mockResolvedValue(null);

  renderPage();

  expect(await screen.findByText('Login page')).toBeInTheDocument();
});