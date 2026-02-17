import { assignVars, createThemeContract, globalStyle } from '@vanilla-extract/css';

export const themeVars = createThemeContract({
    color: {
        background: null,
        text: null,
    },
});

const lightThemeVars = {
    color: {
        background: '#ffffff',
        text: '#111111',
    },
} as const;

const darkThemeVars = {
    color: {
        background: '#111111',
        text: '#f5f5f5',
    },
} as const;

globalStyle(':root', {
    colorScheme: 'light dark',
    vars: assignVars(themeVars, lightThemeVars),
});

globalStyle(':root', {
    '@media': {
        '(prefers-color-scheme: dark)': {
            vars: assignVars(themeVars, darkThemeVars),
            colorScheme: 'dark',
        },
    },
});

globalStyle(':root[data-theme="light"]', {
    colorScheme: 'light',
    vars: assignVars(themeVars, lightThemeVars),
});

globalStyle(':root[data-theme="dark"]', {
    colorScheme: 'dark',
    vars: assignVars(themeVars, darkThemeVars),
});

export const themeNames = ['light', 'dark'] as const;
export type ThemeName = (typeof themeNames)[number];
