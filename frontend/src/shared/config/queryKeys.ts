/** Query key factories shared across the app. */
export const queryKeys = {
    auth: {
        me: ['auth', 'me'] as const,
    },
    user: {
        profile: (userUuid: string) => ['user', 'profile', userUuid] as const,
    },
} as const;
