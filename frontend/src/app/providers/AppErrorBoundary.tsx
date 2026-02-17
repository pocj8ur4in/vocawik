import type { ErrorInfo, ReactNode } from 'react';
import { Component } from 'react';
import * as Sentry from '@sentry/react';

type AppErrorBoundaryProps = {
    children: ReactNode;
};

type AppErrorBoundaryState = {
    hasError: boolean;
};

/** Catches render-time errors and shows a safe fallback screen. */
export default class AppErrorBoundary extends Component<
    AppErrorBoundaryProps,
    AppErrorBoundaryState
> {
    public constructor(props: AppErrorBoundaryProps) {
        super(props);
        this.state = { hasError: false };
    }

    public static getDerivedStateFromError(): AppErrorBoundaryState {
        return { hasError: true };
    }

    public componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
        Sentry.captureException(error, {
            extra: {
                componentStack: errorInfo.componentStack,
            },
        });
    }

    public render(): ReactNode {
        if (this.state.hasError) {
            return <main></main>;
        }

        return this.props.children;
    }
}
