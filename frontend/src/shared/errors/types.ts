/** Normalized error categories used by the client app. */
export type AppErrorKind = 'api' | 'network' | 'unknown';

/** Standard error shape consumed by UI and domain logic. */
export type AppError = {
    kind: AppErrorKind;
    message: string;
    status?: number;
    code?: string;
    details?: unknown;
};

/** Error payload returned by backend APIs. */
export type ApiErrorResponse = {
    code: number;
    message: string;
    status: string;
    timestamp: string;
};
