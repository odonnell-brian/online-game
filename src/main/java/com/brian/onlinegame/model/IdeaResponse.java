package com.brian.onlinegame.model;

import java.time.Instant;
import java.util.Set;

public record IdeaResponse(
        Long id,
        String title,
        String description,
        int votes,
        Instant createdAt,
        IdeaStatus status,
        Set<IdeaStatus> allowedStatuses,
        boolean votable) {

    public static IdeaResponse fromIdea(Idea idea) {
        return new IdeaResponse(
                idea.getId(),
                idea.getTitle(),
                idea.getDescription(),
                idea.getVotes(),
                idea.getCreatedAt(),
                idea.getStatus(),
                idea.getStatus().getAllowedNextStatuses(),
                !idea.getStatus().isTerminal());
    }
}
