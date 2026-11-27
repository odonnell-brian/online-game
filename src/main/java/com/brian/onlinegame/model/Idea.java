package com.brian.onlinegame.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    private int votes;

    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'OPEN'")
    private IdeaStatus status = IdeaStatus.OPEN;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getVotes() {
        return votes;
    }

    public void setVotes(int votes) {
        this.votes = votes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setStatus(IdeaStatus status) {
        this.status = status;
    }

    public IdeaStatus getStatus() {
        return status;
    }
}
