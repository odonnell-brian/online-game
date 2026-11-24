package com.brian.starter.controller;

import com.brian.starter.model.Idea;
import com.brian.starter.model.IdeaResponse;
import com.brian.starter.model.IdeaStatusRequest;
import com.brian.starter.service.IdeaService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ideas")
public class IdeaApiController {
    private final IdeaService ideaService;

    public IdeaApiController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @GetMapping
    public List<IdeaResponse> list() {
        return ideaService.getIdeas().stream().map(IdeaResponse::fromIdea).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdeaResponse create(@Valid @RequestBody Idea idea) {
        return IdeaResponse.fromIdea(ideaService.createIdea(idea));
    }

    @PostMapping("/{id}/vote")
    public IdeaResponse vote(@PathVariable Long id) {
        return IdeaResponse.fromIdea(ideaService.vote(id));
    }

    @PostMapping("/{id}/status")
    public IdeaResponse changeStatus(
            @PathVariable Long id, @Valid @RequestBody IdeaStatusRequest request) {
        return IdeaResponse.fromIdea(ideaService.changeStatus(id, request.status()));
    }
}
