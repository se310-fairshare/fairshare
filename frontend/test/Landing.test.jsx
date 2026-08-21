import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from '../src/pages/Landing.jsx';
import { getCurrentUser } from '../src/api/users';

vi.mock('../src/api/users', () => ({
    getCurrentUser: vi.fn(),
    updateCurrentUser: vi.fn(),
    logout: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
});

it('shows logged-in actions when a session exists', async () => {
    getCurrentUser.mockResolvedValue({ id: 1, username: 'alice' });

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByText('My Groups')).toBeInTheDocument();
});

it('shows sign-up actions when no session exists', async () => {
    getCurrentUser.mockResolvedValue(null);

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByText('Log-in')).toBeInTheDocument();
});