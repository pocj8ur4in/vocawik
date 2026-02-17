import { createBrowserRouter } from 'react-router-dom';

import App from '@/app/App';
import DashboardPage from '@/pages/DashboardPage';
import LoginPage from '@/pages/LoginPage';
import NotFoundPage from '@/pages/NotFoundPage';
import ProtectedRoute from '@/shared/auth/ProtectedRoute';
import { routes } from '@/shared/config/routes';

const router = createBrowserRouter([
    {
        path: routes.home,
        element: <App />,
    },
    {
        path: routes.login,
        element: <LoginPage />,
    },
    {
        element: <ProtectedRoute />,
        children: [
            {
                path: routes.dashboard,
                element: <DashboardPage />,
            },
        ],
    },
    {
        path: routes.notFound,
        element: <NotFoundPage />,
    },
]);

export default router;
