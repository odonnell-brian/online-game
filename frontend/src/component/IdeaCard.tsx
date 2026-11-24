import { Idea } from "../types/ideaTypes";

interface IdeaProps {
  idea: Idea;
  onVote: (id: number) => void;
}

export function IdeaCard(props: IdeaProps) {
  const { idea } = props;

  return (
    <>
      <div className="idea-body">
        <strong>{idea.title}</strong>
        {idea.description && <p>{idea.description}</p>}
        <small>{new Date(idea.createdAt).toLocaleString()}</small>
      </div>
      <div className="idea-votes">
        <span>Votes: {idea.votes}</span>
        <button onClick={() => props.onVote(idea.id)}>Upvote</button>
      </div>
    </>
  );
}
