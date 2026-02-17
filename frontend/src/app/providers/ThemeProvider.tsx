import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { themeNames, type ThemeName } from '@/app/theme.css';

type ThemeContextValue = {
    theme: ThemeName;
    setTheme: (theme: ThemeName) => void;
    toggleTheme: () => void;
};

const THEME_STORAGE_KEY = 'vocawik-theme';
const THEME_ORDER: ThemeName[] = [...themeNames];

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

function isThemeName(value: string): value is ThemeName {
    return THEME_ORDER.includes(value as ThemeName);
}

function getInitialTheme(): ThemeName {
    if (typeof window === 'undefined') {
        return 'light';
    }

    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (stored && isThemeName(stored)) {
        return stored;
    }

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

type ThemeProviderProps = {
    children: ReactNode;
};

/** Provides global theme state and applies theme to the document root. */
export default function ThemeProvider({ children }: ThemeProviderProps) {
    const [theme, setTheme] = useState<ThemeName>(() => getInitialTheme());

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme);
        window.localStorage.setItem(THEME_STORAGE_KEY, theme);
    }, [theme]);

    const value = useMemo<ThemeContextValue>(
        () => ({
            theme,
            setTheme,
            toggleTheme: () =>
                setTheme((prev) => {
                    const index = THEME_ORDER.indexOf(prev);
                    const nextIndex = (index + 1) % THEME_ORDER.length;
                    return THEME_ORDER[nextIndex];
                }),
        }),
        [theme],
    );

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/** Returns current theme context. Must be used under ThemeProvider. */
export function useTheme(): ThemeContextValue {
    const context = useContext(ThemeContext);
    if (!context) {
        throw new Error('useTheme must be used within ThemeProvider.');
    }
    return context;
}
