import { Idea } from "../types/ideaTypes";

export function makeIdea(overrides: Partial<Idea> = {}): Idea {
  return {
    id: 1,
    title: "Test idea",
    description: "A description",
    createdAt: "2026-01-01T12:00:00Z",
    votes: 0,
    ...overrides,
  };
}

/** Builds a minimal fetch Response for stubbing the global fetch. */
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
