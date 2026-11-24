import { Idea, NewIdea } from "../types/ideaTypes";
import { request } from "./http";

const BASE_URL = "/api/ideas";

const listIdeas = () => request<Idea[]>(BASE_URL);

const createIdea = (idea: NewIdea) =>
  request<Idea>(BASE_URL, { method: "POST", body: JSON.stringify(idea) });

const upvote = (id: number) =>
  request<Idea>(`${BASE_URL}/${id}/vote`, { method: "POST" });

export const ideaApi = {
  listIdeas,
  upvote,
  createIdea,
};
