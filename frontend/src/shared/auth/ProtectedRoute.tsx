import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuthStore } from '@/shared/auth/authStore';

/** Route guard skeleton for authenticated pages. */
export default function ProtectedRoute() {
    const location = useLocation();
    const accessToken = useAuthStore((state) => state.accessToken);

    if (!accessToken) {
        return <Navigate to="/login" replace state={{ from: location }} />;
    }

    return <Outlet />;
}
