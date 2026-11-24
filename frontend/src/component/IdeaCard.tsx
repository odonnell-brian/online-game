import { Idea } from "../types/ideaTypes";

interface IdeaProps {
  idea: Idea;
  onVote: (id: number) => void;
}

export function IdeaCard(props: IdeaProps) {
  const { idea } = props;

  return (
    <>
      <strong>{idea.title}</strong>
      <div>{idea.description}</div>
      <div className="idea-footer">
        <span>Votes: {idea.votes}</span>
        <button onClick={() => props.onVote(idea.id)}>Upvote</button>
      </div>
      <small>{new Date(idea.createdAt).toLocaleString()}</small>
    </>
  );
}
