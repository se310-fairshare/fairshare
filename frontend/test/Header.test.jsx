import { expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Header from '../src/components/Header.jsx';

function renderAt(path) {
    render(<MemoryRouter initialEntries={[path]}><Header /></MemoryRouter>);
}

it('#83: links the profile button to the profile page', () => {
    renderAt('/groups');

    expect(screen.getByRole('link', { name: 'My profile' })).toHaveAttribute('href', '/profile');
});

it('#83: shows a home link with the logo away from the home page', () => {
    renderAt('/groups');

    expect(screen.getByRole('link', { name: 'FairShare home' })).toHaveAttribute('href', '/');
});

it('#83: leaves the logo out of the header on the log in page, which also shows it large', () => {
    renderAt('/login');

    expect(screen.queryByRole('link', { name: 'FairShare home' })).not.toBeInTheDocument();
});

it('#84: links to every main section from a signed-in screen', () => {
    renderAt('/groups');

    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getByRole('link', { name: 'Groups' })).toHaveAttribute('href', '/groups');
    expect(within(nav).getByRole('link', { name: 'Individual debts' })).toHaveAttribute('href', '/debts');
    expect(within(nav).getByRole('link', { name: 'My spending' })).toHaveAttribute('href', '/spending');
    expect(within(nav).getByRole('link', { name: 'Profile' })).toHaveAttribute('href', '/profile');
});

it('#84: marks the section the user is in', () => {
    renderAt('/debts');

    expect(screen.getByRole('link', { name: 'Individual debts' })).toHaveClass('active');
    expect(screen.getByRole('link', { name: 'Groups' })).not.toHaveClass('active');
});

it.each(['/login', '/register'])('#84: shows no navigation on %s, before signing in', (path) => {
    renderAt(path);

    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Open menu' })).not.toBeInTheDocument();
});

it('#84 AC5: the menu button opens and closes the phone menu', async () => {
    const user = userEvent.setup();
    renderAt('/groups');

    const button = screen.getByRole('button', { name: 'Open menu' });
    expect(button).toHaveAttribute('aria-expanded', 'false');

    await user.click(button);
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('navigation', { name: 'Main' })).toHaveClass('header-nav-open');

    await user.click(screen.getByRole('button', { name: 'Close menu' }));
    expect(screen.getByRole('navigation', { name: 'Main' })).not.toHaveClass('header-nav-open');
});

it('#84 AC5: picking a link or pressing Escape closes the phone menu', async () => {
    const user = userEvent.setup();
    renderAt('/groups');

    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    await user.click(screen.getByRole('link', { name: 'My spending' }));
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute('aria-expanded', 'false');

    await user.click(screen.getByRole('button', { name: 'Open menu' }));
    await user.keyboard('{Escape}');
    expect(screen.getByRole('button', { name: 'Open menu' })).toHaveAttribute('aria-expanded', 'false');
});
