export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!res.ok) {
    // The backend returns RFC 9457 problem details:
    // { status, title, detail, errors?: [{ field, message }] }
    const body = await res.json().catch(() => ({}));
    const fieldError = body.errors?.[0];
    const msg =
      (fieldError && `${fieldError.field} ${fieldError.message}`) ||
      body.detail ||
      `Request failed (${res.status})`;
    throw new Error(msg);
  }
  return res.json() as Promise<T>;
}