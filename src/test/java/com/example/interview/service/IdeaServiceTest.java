package com.example.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.interview.model.Idea;
import com.example.interview.repository.IdeaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class IdeaServiceTest {

  @Mock private IdeaRepository repository;

  @InjectMocks private IdeaService service;

  @Test
  void getIdeas_sortsByVotesDescThenCreatedAtAsc() {
    Idea idea = idea("A");
    when(repository.findAll(org.mockito.ArgumentMatchers.any(Sort.class))).thenReturn(List.of(idea));

    List<Idea> result = service.getIdeas();

    assertThat(result).containsExactly(idea);
    ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
    verify(repository).findAll(sort.capture());
    assertThat(sort.getValue())
        .isEqualTo(Sort.by(Sort.Order.desc("votes"), Sort.Order.asc("createdAt")));
  }

  @Test
  void createIdea_savesAndReturnsPersistedIdea() {
    Idea input = idea("New");
    Idea saved = idea("New");
    when(repository.save(input)).thenReturn(saved);

    assertThat(service.createIdea(input)).isSameAs(saved);
  }

  @Test
  void vote_incrementsAndReturnsUpdatedIdea() {
    Idea updated = idea("Voted");
    updated.setVotes(1);
    when(repository.incrementVotes(5L)).thenReturn(1);
    when(repository.findById(5L)).thenReturn(Optional.of(updated));

    assertThat(service.vote(5L)).isSameAs(updated);
    verify(repository).incrementVotes(5L);
  }

  @Test
  void vote_throwsWhenNoRowUpdated() {
    when(repository.incrementVotes(99L)).thenReturn(0);

    assertThatThrownBy(() -> service.vote(99L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Idea not found.");
    verify(repository, never()).findById(anyLong());
  }

  @Test
  void vote_throwsWhenIdeaDisappearsAfterUpdate() {
    when(repository.incrementVotes(7L)).thenReturn(1);
    when(repository.findById(7L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.vote(7L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Idea not found.");
  }

  private static Idea idea(String title) {
    Idea idea = new Idea();
    idea.setTitle(title);
    return idea;
  }
}
