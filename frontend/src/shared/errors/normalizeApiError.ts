import { ApiError } from './ApiError';
import type { AppError } from './types';

const DEFAULT_UNKNOWN_MESSAGE = 'Unexpected error occurred.';
const DEFAULT_NETWORK_MESSAGE = 'Network error occurred. Please check your connection.';

/**
 * Converts unknown thrown values into AppError shape.
 * @param input Unknown error value.
 * @returns Normalized app error.
 */
export function normalizeApiError(input: unknown): AppError {
    if (input instanceof ApiError) {
        return {
            kind: 'api',
            message: input.message || DEFAULT_UNKNOWN_MESSAGE,
            status: input.status,
            code: input.code,
            details: input.body,
        };
    }

    if (input instanceof TypeError) {
        return {
            kind: 'network',
            message: DEFAULT_NETWORK_MESSAGE,
        };
    }

    if (input instanceof Error) {
        return {
            kind: 'unknown',
            message: input.message || DEFAULT_UNKNOWN_MESSAGE,
        };
    }

    return {
        kind: 'unknown',
        message: DEFAULT_UNKNOWN_MESSAGE,
        details: input,
    };
}
