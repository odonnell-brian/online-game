import { Idea } from "../types/ideaTypes";
import { IdeaCard } from "./IdeaCard";

interface IdeaListProps {
  ideas: Idea[];
  onVote: (id: number) => void;
}

export function IdeaList(props: IdeaListProps) {
  return (
    <ul className="idea-list">
      {props.ideas.map((idea) => (
        <li key={idea.id} className="idea-card">
          <IdeaCard idea={idea} onVote={props.onVote} />
        </li>
      ))}
    </ul>
  );
}
