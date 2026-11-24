import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IdeaView } from "./IdeaView";
import { ideaApi } from "../api/ideaApi";
import { makeIdea } from "../test/fixtures";

// Stub the API module so these tests cover the component, not the network.
vi.mock("../api/ideaApi", () => ({
  ideaApi: {
    listIdeas: vi.fn(),
    createIdea: vi.fn(),
    upvote: vi.fn(),
  },
}));

const api = vi.mocked(ideaApi);

describe("IdeaView", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("loads and shows ideas on mount", async () => {
    api.listIdeas.mockResolvedValue([makeIdea({ title: "Loaded idea" })]);

    render(<IdeaView />);

    expect(await screen.findByText("Loaded idea")).toBeInTheDocument();
    expect(api.listIdeas).toHaveBeenCalledTimes(1);
  });

  it("creates an idea, reloads the list and clears the form", async () => {
    const created = makeIdea({ id: 2, title: "New idea", description: "Details" });
    api.listIdeas.mockResolvedValueOnce([]).mockResolvedValueOnce([created]);
    api.createIdea.mockResolvedValue(created);
    render(<IdeaView />);

    const title = screen.getByLabelText("Title");
    const description = screen.getByLabelText("Description");
    await userEvent.type(title, "New idea");
    await userEvent.type(description, "Details");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));

    expect(api.createIdea).toHaveBeenCalledWith({
      title: "New idea",
      description: "Details",
    });
    expect(await screen.findByText("New idea")).toBeInTheDocument();
    expect(title).toHaveValue("");
    expect(description).toHaveValue("");
  });

  it("upvotes an idea and shows the refreshed vote count", async () => {
    api.listIdeas
      .mockResolvedValueOnce([makeIdea({ id: 5, votes: 0 })])
      .mockResolvedValueOnce([makeIdea({ id: 5, votes: 1 })]);
    api.upvote.mockResolvedValue(makeIdea({ id: 5, votes: 1 }));
    render(<IdeaView />);

    await userEvent.click(await screen.findByRole("button", { name: "Upvote" }));

    expect(api.upvote).toHaveBeenCalledWith(5);
    expect(await screen.findByText("Votes: 1")).toBeInTheDocument();
  });

  describe("errors", () => {
    it("shows an error when ideas fail to load", async () => {
      api.listIdeas.mockRejectedValue(new Error("Request failed (500)"));

      render(<IdeaView />);

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Could not load ideas: Request failed (500)",
      );
    });

    it("blocks a blank title without calling the API", async () => {
      api.listIdeas.mockResolvedValue([]);
      render(<IdeaView />);

      await userEvent.type(screen.getByLabelText("Title"), "   ");
      await userEvent.click(screen.getByRole("button", { name: "Add" }));

      expect(screen.getByRole("alert")).toHaveTextContent("Title is required");
      expect(api.createIdea).not.toHaveBeenCalled();
    });

    it("shows the server's message and keeps the form when create fails", async () => {
      api.listIdeas.mockResolvedValue([]);
      api.createIdea.mockRejectedValue(new Error("size must be between 0 and 200"));
      render(<IdeaView />);

      const title = screen.getByLabelText("Title");
      await userEvent.type(title, "Too long");
      await userEvent.click(screen.getByRole("button", { name: "Add" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "size must be between 0 and 200",
      );
      expect(title).toHaveValue("Too long");
    });

    it("shows an error when voting fails", async () => {
      api.listIdeas.mockResolvedValue([makeIdea({ id: 5 })]);
      api.upvote.mockRejectedValue(new Error("Not found"));
      render(<IdeaView />);

      await userEvent.click(await screen.findByRole("button", { name: "Upvote" }));

      expect(await screen.findByRole("alert")).toHaveTextContent("Could not vote: Not found");
    });

    it("clears the previous error after a successful action", async () => {
      api.listIdeas.mockResolvedValue([]);
      api.createIdea.mockRejectedValueOnce(new Error("Server down"));
      api.createIdea.mockResolvedValueOnce(makeIdea({ title: "Retry" }));
      render(<IdeaView />);

      await userEvent.type(screen.getByLabelText("Title"), "Retry");
      await userEvent.click(screen.getByRole("button", { name: "Add" }));
      expect(await screen.findByRole("alert")).toBeInTheDocument();

      await userEvent.click(screen.getByRole("button", { name: "Add" }));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
  });
});
