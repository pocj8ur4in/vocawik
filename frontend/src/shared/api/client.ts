import { ApiError, isApiErrorResponse } from '@/shared/errors/ApiError';
import { env } from '@/shared/config/env';

const API_BASE_URL = env.VITE_API_BASE_URL;

type ApiClientHooks = {
    getAccessToken?: () => string | null;
    refreshAccessToken?: () => Promise<string | null>;
    onUnauthorized?: () => void | Promise<void>;
};

type ApiFetchOptions = {
    withAuth?: boolean;
};

let apiClientHooks: ApiClientHooks = {};
let refreshPromise: Promise<string | null> | null = null;

/**
 * Configures hooks for API authentication behavior.
 * @param hooks Token lookup and unauthorized handling hooks.
 */
export function configureApiClient(hooks: ApiClientHooks): void {
    apiClientHooks = hooks;
}

async function refreshAccessToken(): Promise<string | null> {
    if (!apiClientHooks.refreshAccessToken) {
        return null;
    }
    if (!refreshPromise) {
        refreshPromise = apiClientHooks.refreshAccessToken().finally(() => {
            refreshPromise = null;
        });
    }
    return refreshPromise;
}

async function parseApiError(response: Response): Promise<ApiError> {
    let responseBody: unknown;
    try {
        responseBody = await response.json();
    } catch {
        responseBody = undefined;
    }

    if (isApiErrorResponse(responseBody)) {
        return new ApiError({
            status: responseBody.code,
            message: responseBody.message,
            code: responseBody.status,
            body: responseBody,
        });
    }

    return new ApiError({
        status: response.status,
        message: `Request failed: ${response.status}`,
        body: responseBody,
    });
}

async function request(path: string, init: RequestInit | undefined, token: string | null) {
    const headers = new Headers(init?.headers);
    headers.set('Content-Type', 'application/json');

    if (token) {
        headers.set('Authorization', `Bearer ${token}`);
    } else {
        headers.delete('Authorization');
    }

    return fetch(`${API_BASE_URL}${path}`, {
        ...init,
        headers,
    });
}

/**
 * Calls an API endpoint and returns parsed JSON data.
 * @param path API path starting with '/'.
 * @param init Optional fetch options.
 * @param options API call options.
 * @returns Parsed response body.
 * @throws ApiError When the response is not successful.
 */
export async function apiFetch<T>(
    path: string,
    init?: RequestInit,
    options?: ApiFetchOptions,
): Promise<T> {
    const shouldAttachAuth = options?.withAuth ?? true;
    const accessToken = shouldAttachAuth ? (apiClientHooks.getAccessToken?.() ?? null) : null;

    let response = await request(path, init, accessToken);
    if (response.ok) {
        return (await response.json()) as T;
    }

    let error = await parseApiError(response);
    if (shouldAttachAuth && error.status === 401) {
        const refreshedToken = await refreshAccessToken();
        if (refreshedToken) {
            response = await request(path, init, refreshedToken);
            if (response.ok) {
                return (await response.json()) as T;
            }
            error = await parseApiError(response);
        }
    }

    if (error.status === 401) {
        await apiClientHooks.onUnauthorized?.();
    }
    throw error;
}
