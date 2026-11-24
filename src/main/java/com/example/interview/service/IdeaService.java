package com.example.interview.service;

import com.example.interview.model.Idea;
import com.example.interview.repository.IdeaRepository;
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
    if (repository.incrementVotes(id) == 0) {
      throw new IllegalArgumentException("Idea not found.");
    }

    return repository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Idea not found."));
  }
}
