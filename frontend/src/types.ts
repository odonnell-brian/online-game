// Mirror of the backend Item entity. Update alongside Item.java.
export interface Item {
  id: number;
  title: string;
  description: string | null;
  createdAt: string;
}

export type NewItem = Pick<Item, 'title'> & { description: string };
