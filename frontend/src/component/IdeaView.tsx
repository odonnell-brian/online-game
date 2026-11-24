import { FormEvent, useEffect, useState } from "react";
import { Idea } from "../types/ideaTypes";
import { IdeaList } from "./IdeaList";
import { ideaApi } from "../api/ideaApi";

export function IdeaView() {
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    ideaApi.listIdeas().then(setIdeas);
  }, []);

  async function handleVote(id: number) {
    await ideaApi.upvote(id);
    setIdeas(await ideaApi.listIdeas());
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");

    // TODO: validations
    await ideaApi.createIdea({ title, description });
    setIdeas(await ideaApi.listIdeas());
    setTitle("");
    setDescription("");
  }

  return (
    <>
      <h3>Submit Your Idea</h3>
      <form onSubmit={handleSubmit} className="idea-form">
        <label>
          Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} />
        </label>
        <label>
          Description
          <textarea
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        {error && <div className="error">{error}</div>}
        <button type="submit">Add</button>
      </form>
      <h3>Submitted Ideas</h3>
      <IdeaList ideas={ideas} onVote={handleVote} />
    </>
  );
}
