import * as Sentry from '@sentry/react';

import { configureApiClient } from '@/shared/api/client';
import { useAuthStore } from '@/shared/auth/authStore';
import { env } from '@/shared/config/env';

let bootstrapped = false;

/** Initializes one-time app runtime integrations. */
export function bootstrapApp(): void {
    if (bootstrapped) {
        return;
    }
    bootstrapped = true;

    Sentry.init({
        dsn: env.VITE_SENTRY_DSN || undefined,
        environment: env.VITE_SENTRY_ENV || import.meta.env.MODE,
        tracesSampleRate: env.VITE_SENTRY_TRACES_SAMPLE_RATE,
        sendDefaultPii: false,
    });

    configureApiClient({
        getAccessToken: () => useAuthStore.getState().accessToken,
        refreshAccessToken: async () => {
            const response = await fetch(`${env.VITE_API_BASE_URL}/api/v1/auth/refresh`, {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                return null;
            }

            const data = (await response.json().catch(() => null)) as {
                accessToken?: string;
            } | null;
            const accessToken = typeof data?.accessToken === 'string' ? data.accessToken : null;
            if (accessToken) {
                useAuthStore.getState().setAccessToken(accessToken);
            }
            return accessToken;
        },
        onUnauthorized: () => {
            useAuthStore.getState().clearAccessToken();
        },
    });
}
