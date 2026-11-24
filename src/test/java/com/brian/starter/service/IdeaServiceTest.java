package com.brian.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.brian.starter.exception.IdeaNotFoundException;
import com.brian.starter.model.Idea;
import com.brian.starter.model.IdeaStatus;
import com.brian.starter.repository.IdeaRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class IdeaServiceTest {

    private static final Set<IdeaStatus> VOTABLE = IdeaStatus.getNonTerminalStatuses();

    @Mock private IdeaRepository repository;

    @InjectMocks private IdeaService service;

    @Test
    void getIdeas_sortsByVotesDescThenCreatedAtAsc() {
        Idea idea = idea("A");
        when(repository.findAll(org.mockito.ArgumentMatchers.any(Sort.class)))
                .thenReturn(List.of(idea));

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
    void vote_incrementsOnlyNonTerminalIdeasAndReturnsUpdatedIdea() {
        Idea updated = idea("Voted");
        updated.setVotes(1);
        when(repository.incrementVotes(5L, VOTABLE)).thenReturn(1);
        when(repository.findById(5L)).thenReturn(Optional.of(updated));

        assertThat(service.vote(5L)).isSameAs(updated);
        verify(repository).incrementVotes(5L, VOTABLE);
    }

    @Test
    void vote_throwsNotFoundWhenNoRowUpdatedAndIdeaMissing() {
        when(repository.incrementVotes(99L, VOTABLE)).thenReturn(0);
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.vote(99L))
                .isInstanceOf(IdeaNotFoundException.class)
                .hasMessage("Idea 99 not found.");
    }

    @Test
    void vote_rejectsTerminalIdeaWhenNoRowUpdated() {
        when(repository.incrementVotes(3L, VOTABLE)).thenReturn(0);
        when(repository.existsById(3L)).thenReturn(true);

        // TODO: switch to the 409 exception once it exists
        assertThatThrownBy(() -> service.vote(3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Idea no longer votable.");
    }

    @Test
    void vote_throwsWhenIdeaDisappearsAfterUpdate() {
        when(repository.incrementVotes(7L, VOTABLE)).thenReturn(1);
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vote(7L))
                .isInstanceOf(IdeaNotFoundException.class)
                .hasMessage("Idea 7 not found.");
    }

    private static Idea idea(String title) {
        Idea idea = new Idea();
        idea.setTitle(title);
        return idea;
    }
}
