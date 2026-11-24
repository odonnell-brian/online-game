export interface Idea {
  id: number;
  title: string;
  description: string | null;
  createdAt: string;
  votes: number;
}

export type NewIdea = Pick<Idea, "title"> & { description: string };
