import { describe, expect, it, vi } from "vitest";
import { request } from "./http";
import { jsonResponse } from "../test/fixtures";

describe("request", () => {
  it("returns the parsed JSON body on success", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(request("/api/x")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith("/api/x", {
      headers: { "Content-Type": "application/json" },
    });
  });

  it("passes options through to fetch", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal("fetch", fetchMock);

    await request("/api/x", { method: "POST", body: "{}" });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/x",
      expect.objectContaining({ method: "POST", body: "{}" }),
    );
  });

  it("throws the first field error from a validation problem", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse(
          {
            status: 400,
            detail: "One or more fields are invalid.",
            errors: [{ field: "title", message: "must not be blank" }],
          },
          400,
        ),
      ),
    );

    await expect(request("/api/x")).rejects.toThrow("title must not be blank");
  });

  it("falls back to the problem's detail", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        jsonResponse({ status: 404, detail: "Idea 9 not found." }, 404),
      ),
    );

    await expect(request("/api/x")).rejects.toThrow("Idea 9 not found.");
  });

  it("falls back to the status code when the body is not JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("oops", { status: 500 })),
    );

    await expect(request("/api/x")).rejects.toThrow("Request failed (500)");
  });
});
