package com.brian.onlinegame.service;

import com.brian.onlinegame.exception.IdeaNotFoundException;
import com.brian.onlinegame.model.Idea;
import com.brian.onlinegame.model.IdeaStatus;
import com.brian.onlinegame.repository.IdeaRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IdeaService {

    private final IdeaRepository repository;

    public IdeaService(IdeaRepository repository) {
        this.repository = repository;
    }

    public List<Idea> getIdeas() {
        return repository.findAll(Sort.by(Sort.Order.desc("votes"), Sort.Order.asc("createdAt")));
    }

    public Idea createIdea(Idea idea) {
        return repository.save(idea);
    }

    @Transactional
    public Idea vote(long id) {
        if (repository.incrementVotes(id, IdeaStatus.getNonTerminalStatuses()) == 0) {
            if (!repository.existsById(id)) throw new IdeaNotFoundException(id);
            else throw new IllegalArgumentException("Idea no longer votable.");
        }

        return repository.findById(id).orElseThrow(() -> new IdeaNotFoundException(id));
    }

    @Transactional
    public Idea changeStatus(long id, IdeaStatus newStatus) {
        Idea idea = repository.findById(id).orElseThrow(() -> new IdeaNotFoundException(id));

        if (!idea.getStatus().isAllowedNextStatus(newStatus)) {
            throw new IllegalArgumentException("Invalid status transition.");
        }

        idea.setStatus(newStatus);
        repository.save(idea);
        return idea;
    }
}
