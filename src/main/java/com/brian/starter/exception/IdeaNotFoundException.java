package com.brian.starter.exception;

/** Thrown when an idea with the requested id does not exist. Mapped to 404. */
public class IdeaNotFoundException extends RuntimeException {

    public IdeaNotFoundException(long id) {
        super("Idea " + id + " not found.");
    }
}
