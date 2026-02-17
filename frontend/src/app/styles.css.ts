import { globalStyle } from '@vanilla-extract/css';
import { themeVars } from '@/app/theme.css';

globalStyle('html', {
    colorScheme: 'light dark',
});

globalStyle('*, *::before, *::after', {
    boxSizing: 'border-box',
});

globalStyle('html, body, #root', {
    margin: 0,
    padding: 0,
    minHeight: '100%',
});

globalStyle('body', {
    backgroundColor: themeVars.color.background,
    color: themeVars.color.text,
});

globalStyle('html', {
    backgroundColor: themeVars.color.background,
    color: themeVars.color.text,
});

globalStyle('*', {
    scrollbarWidth: 'none',
    msOverflowStyle: 'none',
});

globalStyle('*::-webkit-scrollbar', {
    display: 'none',
});
