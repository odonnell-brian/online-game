package com.example.interview.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.interview.model.Idea;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Runs against an in-memory H2 database (DataJpaTest replaces the file-based datasource). */
@DataJpaTest
class IdeaRepositoryTest {

    @Autowired private IdeaRepository repository;

    @Autowired private TestEntityManager em;

    @Test
    void incrementVotes_increasesVoteCountByOne() {
        Idea idea = new Idea();
        idea.setTitle("Persisted");
        idea.setVotes(2);
        Long id = em.persistAndFlush(idea).getId();
        em.clear();

        int updated = repository.incrementVotes(id);
        em.clear(); // bulk update bypasses the persistence context

        assertThat(updated).isEqualTo(1);
        assertThat(repository.findById(id)).get().extracting(Idea::getVotes).isEqualTo(3);
    }

    @Test
    void incrementVotes_returnsZeroForUnknownId() {
        assertThat(repository.incrementVotes(12345L)).isZero();
    }
}
