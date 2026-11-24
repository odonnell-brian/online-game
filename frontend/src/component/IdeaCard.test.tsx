import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { IdeaCard } from "./IdeaCard";
import { makeIdea } from "../test/fixtures";

describe("IdeaCard", () => {
  it("shows the idea's title, description and votes", () => {
    render(
      <IdeaCard
        idea={makeIdea({ title: "Dark mode", description: "Easier on the eyes", votes: 3 })}
        onVote={() => {}}
      />,
    );

    expect(screen.getByText("Dark mode")).toBeInTheDocument();
    expect(screen.getByText("Easier on the eyes")).toBeInTheDocument();
    expect(screen.getByText("Votes: 3")).toBeInTheDocument();
  });

  it("calls onVote with the idea's id when Upvote is clicked", async () => {
    const onVote = vi.fn();
    render(<IdeaCard idea={makeIdea({ id: 42 })} onVote={onVote} />);

    await userEvent.click(screen.getByRole("button", { name: "Upvote" }));

    expect(onVote).toHaveBeenCalledTimes(1);
    expect(onVote).toHaveBeenCalledWith(42);
  });
});
