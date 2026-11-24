package com.brian.starter.repository;

import com.brian.starter.model.Idea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdeaRepository extends JpaRepository<Idea, Long> {

    @Modifying
    @Query("update Idea i set i.votes = i.votes + 1 where i.id = :id")
    int incrementVotes(@Param("id") Long id);
}
