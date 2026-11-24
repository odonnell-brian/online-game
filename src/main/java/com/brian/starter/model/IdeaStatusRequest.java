package com.brian.starter.model;

import jakarta.validation.constraints.NotNull;

public record IdeaStatusRequest(@NotNull IdeaStatus status) {}
