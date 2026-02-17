import type { ApiErrorResponse } from './types';

/** Runtime error type for failed API requests. */
export class ApiError extends Error {
    public readonly status: number;
    public readonly code?: string;
    public readonly body?: unknown;

    /**
     * Creates an API error object from HTTP and payload context.
     * @param params Error properties extracted from a failed response.
     */
    constructor(params: { status: number; message: string; code?: string; body?: unknown }) {
        super(params.message);
        this.name = 'ApiError';
        this.status = params.status;
        this.code = params.code;
        this.body = params.body;
    }
}

/**
 * Type guard for backend error payloads.
 * @param value Unknown response body.
 * @returns True when the value matches ApiErrorResponse.
 */
export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
    if (!value || typeof value !== 'object') {
        return false;
    }
    const candidate = value as Partial<ApiErrorResponse>;
    return (
        typeof candidate.code === 'number' &&
        typeof candidate.message === 'string' &&
        typeof candidate.status === 'string'
    );
}
