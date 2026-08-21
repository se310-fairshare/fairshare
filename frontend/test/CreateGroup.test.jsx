import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import CreateGroup from '../src/pages/CreateGroup.jsx';
import { createGroup } from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => ({
    ...(await vi.importActual('react-router-dom')),
    useNavigate: () => mockNavigate,
}));

function renderPage() {
    render(
        <MemoryRouter>
            <CreateGroup />
        </MemoryRouter>
    );
}

describe('CreateGroup', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('AC1: navigates to the new group page after creation', async () => {
        createGroup.mockResolvedValue({ group: { id: 7, name: 'Flat 3' } });

        renderPage();
        await userEvent.type(screen.getByLabelText(/group name/i), 'Flat 3');
        await userEvent.click(screen.getByRole('button', { name: /create group/i }));

        expect(mockNavigate).toHaveBeenCalledWith('/groups/7');
    });

    it('AC3: shows an inline error when the name is rejected as blank', async () => {
        createGroup.mockResolvedValue({ errors: { name: 'Group name is required' } });

        renderPage();
        await userEvent.type(screen.getByLabelText(/group name/i), '   ');
        await userEvent.click(screen.getByRole('button', { name: /create group/i }));

        expect(await screen.findByText('Group name is required')).toBeInTheDocument();
        expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('AC4: shows an inline error when the name is too long', async () => {
        createGroup.mockResolvedValue({ errors: { name: 'Group name must be at most 50 characters' } });

        renderPage();
        await userEvent.type(screen.getByLabelText(/group name/i), 'a'.repeat(51));
        await userEvent.click(screen.getByRole('button', { name: /create group/i }));

        expect(await screen.findByText(/at most 50 characters/i)).toBeInTheDocument();
        expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('shows an inline error when the description is too long', async () => {
        createGroup.mockResolvedValue({
            errors: { description: 'Description must be at most 255 characters' }
        });

        renderPage();
        await userEvent.type(screen.getByLabelText(/group name/i), 'Flat 3');
        await userEvent.click(screen.getByRole('button', { name: /create group/i }));

        expect(await screen.findByText(/at most 255 characters/i)).toBeInTheDocument();
    });
});