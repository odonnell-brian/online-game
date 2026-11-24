import { FormEvent, useEffect, useState } from "react";
import { Idea } from "../types/ideaTypes";
import { IdeaList } from "./IdeaList";
import { ideaApi } from "../api/ideaApi";

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : "Something went wrong";
}

export function IdeaView() {
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    ideaApi
      .listIdeas()
      .then(setIdeas)
      .catch((err) => setError(`Could not load ideas: ${errorMessage(err)}`));
  }, []);

  async function handleVote(id: number) {
    setError(null);
    try {
      await ideaApi.upvote(id);
      setIdeas(await ideaApi.listIdeas());
    } catch (err) {
      setError(`Could not vote: ${errorMessage(err)}`);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!title.trim()) {
      setError("Title is required");
      return;
    }

    try {
      await ideaApi.createIdea({ title, description });
    } catch (err) {
      // Keep the form filled in so the user can fix it and retry.
      setError(errorMessage(err));
      return;
    }

    setTitle("");
    setDescription("");
    try {
      setIdeas(await ideaApi.listIdeas());
    } catch (err) {
      setError(`Idea saved, but the list could not be refreshed: ${errorMessage(err)}`);
    }
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
        {error && (
          <div className="error" role="alert">
            {error}
          </div>
        )}
        <button type="submit">Add</button>
      </form>
      <h3>Submitted Ideas</h3>
      <IdeaList ideas={ideas} onVote={handleVote} />
    </>
  );
}
