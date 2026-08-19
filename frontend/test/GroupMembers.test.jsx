import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import GroupMembers from '../src/pages/GroupMembers.jsx';
import {
    addGroupMember,
    getGroupMembers,
    removeGroupMember,
} from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    addGroupMember: vi.fn(),
    getGroupMembers: vi.fn(),
    removeGroupMember: vi.fn(),
}));

const alice = {
    userId: 1,
    username: 'alice',
    email: 'alice@test.com',
    netBalance: 0,
    currentUser: true,
};

const bob = {
    userId: 2,
    username: 'bob',
    email: 'bob@test.com',
    netBalance: 0,
    currentUser: false,
};

beforeEach(() => {
    vi.clearAllMocks();
    getGroupMembers.mockResolvedValue({ members: [alice] });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/5/members']}>
            <Routes>
                <Route path="/groups/:id/members" element={<GroupMembers />} />
                <Route path="/groups" element={<p>Groups overview</p>} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC1: adds a registered user by email', async () => {
    const user = userEvent.setup();
    addGroupMember.mockResolvedValue({ member: bob });
    renderPage();

    expect(await screen.findByText('alice')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Email or username'), 'bob@test.com');
    await user.click(screen.getByRole('button', { name: 'Add Member' }));

    expect(addGroupMember).toHaveBeenCalledWith('5', 'bob@test.com');
    expect(await screen.findByText('bob')).toBeInTheDocument();
});

it('AC2: displays the duplicate-member message', async () => {
    const user = userEvent.setup();
    addGroupMember.mockResolvedValue({
        error: 'User is already a member of this group',
    });
    renderPage();

    await screen.findByText('alice');
    await user.type(screen.getByLabelText('Email or username'), 'alice');
    await user.click(screen.getByRole('button', { name: 'Add Member' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
        'User is already a member of this group');
});

it('AC4: removes a member with a zero balance', async () => {
    const user = userEvent.setup();
    getGroupMembers.mockResolvedValue({ members: [alice, bob] });
    removeGroupMember.mockResolvedValue({});
    renderPage();

    expect(await screen.findByText('bob')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    const dialog = screen.getByRole('dialog', { name: 'Remove bob?' });
    expect(removeGroupMember).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));

    expect(removeGroupMember).toHaveBeenCalledWith('5', 2);
    expect(screen.queryByText('bob')).not.toBeInTheDocument();
});

it('AC5: displays a blocked-removal message', async () => {
    const user = userEvent.setup();
    getGroupMembers.mockResolvedValue({ members: [alice, bob] });
    removeGroupMember.mockResolvedValue({
        error: "The member's balance must be settled before removal",
    });
    renderPage();

    await screen.findByText('bob');
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    const dialog = screen.getByRole('dialog', { name: 'Remove bob?' });
    await user.click(within(dialog).getByRole('button', { name: 'Remove' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
        "The member's balance must be settled before removal");
});

it('AC6: confirms before the current user leaves and returns to the overview', async () => {
    const user = userEvent.setup();
    getGroupMembers.mockResolvedValue({ members: [alice, bob] });
    removeGroupMember.mockResolvedValue({});
    renderPage();

    await screen.findByText('alice');
    await user.click(screen.getByRole('button', { name: 'Leave group' }));
    const dialog = screen.getByRole('dialog', { name: 'Leave group?' });

    expect(screen.getByText('You will lose access to this group.')).toBeInTheDocument();
    expect(removeGroupMember).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Leave group' }));

    expect(await screen.findByText('Groups overview')).toBeInTheDocument();
});

it('keeps the member when removal is cancelled', async () => {
    const user = userEvent.setup();
    getGroupMembers.mockResolvedValue({ members: [alice, bob] });
    renderPage();

    expect(await screen.findByText('bob')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(removeGroupMember).not.toHaveBeenCalled();
    expect(screen.getByText('bob')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('AC7: shows an authorisation error to a non-member', async () => {
    getGroupMembers.mockResolvedValue({
        error: 'You must be a group member to manage its members',
    });
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
        'You must be a group member to manage its members');
    expect(screen.queryByText('alice')).not.toBeInTheDocument();
});
