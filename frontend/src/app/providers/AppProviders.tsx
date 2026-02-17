import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import AppErrorBoundary from '@/app/providers/AppErrorBoundary';
import ThemeProvider from '@/app/providers/ThemeProvider';

const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            staleTime: 30_000,
            gcTime: 5 * 60_000,
            retry: (failureCount, error) => {
                const status =
                    typeof error === 'object' && error && 'status' in error
                        ? Number((error as { status?: unknown }).status)
                        : undefined;
                if (typeof status === 'number' && status >= 500) {
                    return failureCount < 2;
                }
                return false;
            },
            refetchOnWindowFocus: false,
            refetchOnReconnect: true,
            refetchOnMount: false,
        },
        mutations: {
            retry: false,
        },
    },
});

type AppProvidersProps = {
    children: ReactNode;
};

/** Provides global app-level providers. */
export default function AppProviders({ children }: AppProvidersProps) {
    return (
        <AppErrorBoundary>
            <ThemeProvider>
                <QueryClientProvider client={queryClient}>
                    {children}
                    {import.meta.env.DEV ? <ReactQueryDevtools initialIsOpen={false} /> : null}
                </QueryClientProvider>
            </ThemeProvider>
        </AppErrorBoundary>
    );
}
