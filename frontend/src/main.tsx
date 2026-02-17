import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';

import router from '@/app/router';
import AppProviders from '@/app/providers/AppProviders';
import { bootstrapApp } from '@/app/providers/bootstrap';
import '@/app/styles.css.ts';

bootstrapApp();

ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
        <AppProviders>
            <RouterProvider router={router} />
        </AppProviders>
    </React.StrictMode>,
);

requestAnimationFrame(() => {
    document.getElementById('boot-splash')?.remove();
});
