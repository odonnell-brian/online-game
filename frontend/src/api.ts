import type { Item, NewItem } from './types';

// Thin API client: keep all fetch calls here so components stay clean.
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    const msg = body.errors?.[0]?.defaultMessage || body.message || `Request failed (${res.status})`;
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}

export const listItems = () => request<Item[]>('/api/items');
export const createItem = (item: NewItem) =>
  request<Item>('/api/items', { method: 'POST', body: JSON.stringify(item) });
