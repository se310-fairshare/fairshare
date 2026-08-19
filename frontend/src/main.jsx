import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import UserProfile from './UserProfile.jsx';
import GroupsOverview from './pages/GroupsOverview.jsx';
import CreateGroup from './pages/CreateGroup.jsx';
import GroupPage from './pages/GroupPage.jsx';
import GroupMembers from './pages/GroupMembers.jsx';
import './index.css';

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Navigate to="/groups" replace />} />
                <Route path="/register" element={<UserProfile />} />
                <Route path="/groups" element={<GroupsOverview />} />
                <Route path="/groups/new" element={<CreateGroup />} />
                <Route path="/groups/:id" element={<GroupPage />} />
                <Route path="/groups/:id/members" element={<GroupMembers />} />
            </Routes>
        </BrowserRouter>
    </StrictMode>,
);
