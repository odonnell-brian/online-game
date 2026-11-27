package com.brian.onlinegame.model;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum IdeaStatus {
    OPEN,
    PLANNED,
    DONE,
    CLOSED;

    private Set<IdeaStatus> allowedNextStatuses;

    static {
        OPEN.allowedNextStatuses = EnumSet.of(PLANNED, DONE, CLOSED);
        PLANNED.allowedNextStatuses = EnumSet.of(DONE, CLOSED);
        DONE.allowedNextStatuses = EnumSet.noneOf(IdeaStatus.class);
        CLOSED.allowedNextStatuses = EnumSet.noneOf(IdeaStatus.class);
    }

    public boolean isTerminal() {
        return allowedNextStatuses.isEmpty();
    }

    public Set<IdeaStatus> getAllowedNextStatuses() {
        return Collections.unmodifiableSet(allowedNextStatuses);
    }

    public boolean isAllowedNextStatus(IdeaStatus nextStatus) {
        return allowedNextStatuses.contains(nextStatus);
    }

    public static Set<IdeaStatus> getNonTerminalStatuses() {
        return Arrays.stream(IdeaStatus.values())
                .filter(Predicate.not(IdeaStatus::isTerminal))
                .collect(Collectors.toSet());
    }
}
