package com.brian.onlinegame.model;

import jakarta.validation.constraints.NotNull;

public record IdeaStatusRequest(@NotNull IdeaStatus status) {}
