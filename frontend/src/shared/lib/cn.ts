import { clsx, type ClassValue } from 'clsx';

/** Joins conditional class names for component styling. */
export function cn(...inputs: ClassValue[]): string {
    return clsx(inputs);
}
