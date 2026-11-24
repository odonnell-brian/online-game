package com.brian.starter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.brian.starter.model.Idea;
import com.brian.starter.model.IdeaStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Set;

/** Runs against an in-memory H2 database (DataJpaTest replaces the file-based datasource). */
@DataJpaTest
class IdeaRepositoryTest {

    @Autowired private IdeaRepository repository;

    @Autowired private TestEntityManager em;

    private static final Set<IdeaStatus> VOTABLE = IdeaStatus.getNonTerminalStatuses();

    @Test
    void incrementVotes_increasesVoteCountByOne() {
        Long id = persist(IdeaStatus.OPEN, 2);

        int updated = repository.incrementVotes(id, VOTABLE);
        em.clear(); // bulk update bypasses the persistence context

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(id)).get().extracting(Idea::getVotes).isEqualTo(3);
    }

    @Test
    void incrementVotes_returnsZeroForUnknownId() {
        assertThat(repository.incrementVotes(12345L, VOTABLE)).isZero();
    }

    @Test
    void incrementVotes_skipsTerminalIdea() {
        Long id = persist(IdeaStatus.DONE, 2);

        int updated = repository.incrementVotes(id, VOTABLE);
        em.clear();

        assertThat(updated).isZero();
        assertThat(repository.findById(id)).get().extracting(Idea::getVotes).isEqualTo(2);
    }

    private Long persist(IdeaStatus status, int votes) {
        Idea idea = new Idea();
        idea.setTitle("Persisted");
        idea.setStatus(status);
        idea.setVotes(votes);
        Long id = em.persistAndFlush(idea).getId();
        em.clear();
        return id;
    }
}
