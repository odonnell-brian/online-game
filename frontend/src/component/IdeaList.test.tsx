import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { IdeaList } from "./IdeaList";
import { makeIdea } from "../test/fixtures";

describe("IdeaList", () => {
  it("renders one list item per idea, in order", () => {
    const ideas = [
      makeIdea({ id: 1, title: "First" }),
      makeIdea({ id: 2, title: "Second" }),
    ];
    render(<IdeaList ideas={ideas} onVote={() => {}} />);

    const items = screen.getAllByRole("listitem");
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText("First")).toBeInTheDocument();
    expect(within(items[1]).getByText("Second")).toBeInTheDocument();
  });

  it("renders an empty list when there are no ideas", () => {
    render(<IdeaList ideas={[]} onVote={() => {}} />);

    expect(screen.getByRole("list")).toBeEmptyDOMElement();
  });
});
