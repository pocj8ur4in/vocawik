import { describe, expect, it, vi } from 'vitest';

import { apiFetch, configureApiClient } from '@/shared/api/client';
import { ApiError } from '@/shared/errors/ApiError';

describe('apiFetch', () => {
    it('retries once after refresh when first request is unauthorized', async () => {
        configureApiClient({
            getAccessToken: () => 'expired-token',
            refreshAccessToken: async () => 'new-token',
        });

        const fetchMock = vi
            .fn()
            .mockResolvedValueOnce({
                ok: false,
                status: 401,
                json: async () => ({
                    code: 401,
                    message: 'Authentication required.',
                    status: 'UNAUTHORIZED',
                    timestamp: '2026-02-16T00:00:00',
                }),
            })
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ ok: true }),
            });
        vi.stubGlobal('fetch', fetchMock);

        const result = await apiFetch<{ ok: boolean }>('/secure');

        expect(result).toEqual({ ok: true });
        expect(fetchMock).toHaveBeenCalledTimes(2);
        const [, retryInit] = fetchMock.mock.calls[1] as [string, RequestInit];
        const retryHeaders = retryInit.headers as Headers;
        expect(retryHeaders.get('Authorization')).toBe('Bearer new-token');
    });

    it('injects Authorization header when access token exists', async () => {
        configureApiClient({
            getAccessToken: () => 'test-token',
        });

        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ok: true }),
        });
        vi.stubGlobal('fetch', fetchMock);

        await apiFetch<{ ok: boolean }>('/secure');

        const [, requestInit] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = requestInit.headers as Headers;
        expect(headers.get('Authorization')).toBe('Bearer test-token');
    });

    it('does not inject Authorization header when withAuth is false', async () => {
        configureApiClient({
            getAccessToken: () => 'test-token',
        });

        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ok: true }),
        });
        vi.stubGlobal('fetch', fetchMock);

        await apiFetch<{ ok: boolean }>('/public', undefined, { withAuth: false });

        const [, requestInit] = fetchMock.mock.calls[0] as [string, RequestInit];
        const headers = requestInit.headers as Headers;
        expect(headers.get('Authorization')).toBeNull();
    });

    it('returns JSON payload when request succeeds', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ok: true }),
        });
        vi.stubGlobal('fetch', fetchMock);

        const result = await apiFetch<{ ok: boolean }>('/health');

        expect(result).toEqual({ ok: true });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('throws ApiError with backend error response fields', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: false,
            status: 401,
            json: async () => ({
                code: 401,
                message: 'Authentication required.',
                status: 'UNAUTHORIZED',
                timestamp: '2026-02-16T00:00:00',
            }),
        });
        vi.stubGlobal('fetch', fetchMock);

        await expect(apiFetch('/secure')).rejects.toMatchObject({
            name: 'ApiError',
            status: 401,
            code: 'UNAUTHORIZED',
            message: 'Authentication required.',
        });
    });

    it('throws fallback ApiError when backend body is not standard error response', async () => {
        const fetchMock = vi.fn().mockResolvedValue({
            ok: false,
            status: 500,
            json: async () => ({ error: 'internal' }),
        });
        vi.stubGlobal('fetch', fetchMock);

        await expect(apiFetch('/broken')).rejects.toBeInstanceOf(ApiError);
    });

    it('calls onUnauthorized hook when api error status is 401', async () => {
        const onUnauthorized = vi.fn();
        configureApiClient({
            refreshAccessToken: async () => null,
            onUnauthorized,
        });

        const fetchMock = vi.fn().mockResolvedValue({
            ok: false,
            status: 401,
            json: async () => ({
                code: 401,
                message: 'Authentication required.',
                status: 'UNAUTHORIZED',
                timestamp: '2026-02-16T00:00:00',
            }),
        });
        vi.stubGlobal('fetch', fetchMock);

        await expect(apiFetch('/secure')).rejects.toBeInstanceOf(ApiError);
        expect(onUnauthorized).toHaveBeenCalledTimes(1);
    });
});
