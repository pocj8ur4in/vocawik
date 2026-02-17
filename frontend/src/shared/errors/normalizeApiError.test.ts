import { describe, expect, it } from 'vitest';

import { ApiError } from '@/shared/errors/ApiError';
import { normalizeApiError } from '@/shared/errors/normalizeApiError';

describe('normalizeApiError', () => {
    it('maps ApiError into AppError api shape', () => {
        const error = new ApiError({
            status: 403,
            code: 'FORBIDDEN',
            message: 'Access denied.',
            body: { code: 403 },
        });

        expect(normalizeApiError(error)).toEqual({
            kind: 'api',
            message: 'Access denied.',
            status: 403,
            code: 'FORBIDDEN',
            details: { code: 403 },
        });
    });

    it('maps TypeError into network AppError', () => {
        const result = normalizeApiError(new TypeError('Failed to fetch'));

        expect(result.kind).toBe('network');
        expect(result.message).toContain('Network');
    });

    it('maps unknown values into unknown AppError', () => {
        const result = normalizeApiError({ foo: 'bar' });

        expect(result.kind).toBe('unknown');
        expect(result.details).toEqual({ foo: 'bar' });
    });
});
