import { FormEvent, useEffect, useState } from 'react';
import { listItems, createItem } from './api';
import type { Item } from './types';

export default function App() {
  const [items, setItems] = useState<Item[]>([]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listItems()
      .then(setItems)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
        console.log("creating...");
        const t0 = performance.now();
        const created = await createItem({ title, description });
        const t1 = performance.now();
        console.log(`request: ${(t1 - t0).toFixed(0)}ms`);

        setItems((prev) => [created, ...prev]);
        setTitle('');
        setDescription('');

        requestAnimationFrame(() => {
          console.log(`state update to paint: ${(performance.now() - t1).toFixed(0)}ms`);
        });
    } catch (err) {
      setError((err as Error).message);
    }
  }

  return (
    <main>
      <h1>Interview Starter</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label>
          Description
          <textarea rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit">Add</button>
      </form>

      {loading ? (
        <p>Loading...</p>
      ) : (
        <ul>
          {items.map((i) => (
            <li key={i.id}>
              <strong>{i.title}</strong>
              <div>{i.description}</div>
              <small>{i.createdAt}</small>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
