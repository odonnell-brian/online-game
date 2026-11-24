package com.brian.starter.controller;

import com.brian.starter.model.Idea;
import com.brian.starter.service.IdeaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ideas")
public class IdeaApiController {
    private final IdeaService ideaService;

    public IdeaApiController(IdeaService ideaService) {
        this.ideaService = ideaService;
    }

    @GetMapping
    public List<Idea> list() {
        return ideaService.getIdeas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Idea create(@Valid @RequestBody Idea idea) {
        return ideaService.createIdea(idea);
    }

    @PostMapping("/{id}/vote")
    public Idea vote(@PathVariable Long id) {
        return ideaService.vote(id);
    }
}
