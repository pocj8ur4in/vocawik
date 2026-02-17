import { z } from 'zod';

const envSchema = z.object({
    VITE_API_BASE_URL: z.url(),
    VITE_SENTRY_DSN: z.string().optional(),
    VITE_SENTRY_ENV: z.string().default('local'),
    VITE_SENTRY_TRACES_SAMPLE_RATE: z.coerce.number().min(0).max(1).default(0),
});

const FALLBACK_ENV = {
    VITE_API_BASE_URL: 'http://localhost:8080',
    VITE_SENTRY_DSN: undefined,
    VITE_SENTRY_ENV: 'local',
    VITE_SENTRY_TRACES_SAMPLE_RATE: 0,
} as const;

const parsedEnv = envSchema.safeParse(import.meta.env);

if (!parsedEnv.success) {
    const summary = parsedEnv.error.issues
        .map((issue) => `${issue.path.join('.')}: ${issue.message}`)
        .join(', ');
    const message = `Invalid VITE_* environment values (${summary}).`;

    if (import.meta.env.PROD) {
        throw new Error(message);
    }
    console.warn(message);
}

/** Validated frontend environment variables with safe local fallbacks. */
export const env = parsedEnv.success ? parsedEnv.data : FALLBACK_ENV;
