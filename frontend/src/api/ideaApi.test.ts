import { describe, expect, it, vi } from "vitest";
import { ideaApi } from "./ideaApi";
import { jsonResponse, makeIdea } from "../test/fixtures";

function stubFetch(body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(jsonResponse(body));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("ideaApi", () => {
  it("listIdeas GETs /api/ideas", async () => {
    const ideas = [makeIdea()];
    const fetchMock = stubFetch(ideas);

    await expect(ideaApi.listIdeas()).resolves.toEqual(ideas);
    expect(fetchMock).toHaveBeenCalledWith("/api/ideas", expect.anything());
  });

  it("createIdea POSTs the new idea as JSON", async () => {
    const fetchMock = stubFetch(makeIdea({ title: "New" }));

    await ideaApi.createIdea({ title: "New", description: "Desc" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/ideas",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ title: "New", description: "Desc" }),
      }),
    );
  });

  it("upvote POSTs to the idea's vote endpoint", async () => {
    const fetchMock = stubFetch(makeIdea({ id: 7, votes: 1 }));

    await ideaApi.upvote(7);

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/ideas/7/vote",
      expect.objectContaining({ method: "POST" }),
    );
  });
});
