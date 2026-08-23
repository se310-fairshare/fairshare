import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {createBrowserRouter, redirect, RouterProvider} from 'react-router-dom';
import UserProfile from './UserProfile.jsx';
import GroupsOverview from './pages/GroupsOverview.jsx';
import CreateGroup from './pages/CreateGroup.jsx';
import GroupPage from './pages/GroupPage.jsx';
import Landing from './pages/Landing.jsx';
import GroupMembers from './pages/GroupMembers.jsx';
import ViewBalance from './pages/ViewBalance.jsx';
import MemberBalance from './pages/MemberBalance.jsx';
import AddExpense from './pages/AddExpense.jsx';
import Login from './pages/Login.jsx';
import UserManagement from './pages/UserManagement.jsx';
import './index.css';
import App from './App.jsx';
import {getCurrentUser} from "./api/users.js";
import EditExpense from './pages/ManageExpense.jsx';

/** Sends visitors without a session to the login page before the route renders. */
async function requireAuth() {
    const user = await getCurrentUser();
    if (!user) {
        throw redirect('/login');
    }
    return user;
}

const router = createBrowserRouter([
    {
        path: '/',
        element: <App/>,
        children: [
            {
                path: '/',
                element: <Landing/>,
            },
            {
                path: '/login',
                element: <Login/>,
            },
            {
                path: '/register',
                element: <UserProfile/>,
            },
            {
                path: '/profile',
                element: <UserManagement/>,
                loader: requireAuth
            },
            {
                path: 'groups',
                element: <GroupsOverview/>,
                loader: requireAuth
            },
            {
                path: '/groups/new',
                element: <CreateGroup/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id',
                element: <GroupPage/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id/members',
                element: <GroupMembers/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id/balance',
                element: <ViewBalance/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id/balance/:memberId',
                element: <MemberBalance/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id/expenses/new',
                element: <AddExpense/>,
                loader: requireAuth
            },
            {
                path: '/groups/:id/expenses/:expenseId/edit',
                element: <EditExpense/>,
                loader: requireAuth
            }
        ]
    }
]);

createRoot(document.getElementById('root')).render(
    <StrictMode>
        <RouterProvider router={router}/>
    </StrictMode>,
);
