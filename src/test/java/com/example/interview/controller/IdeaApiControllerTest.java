package com.example.interview.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interview.model.Idea;
import com.example.interview.service.IdeaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IdeaApiController.class)
class IdeaApiControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private IdeaService ideaService;

    @Test
    void list_returnsIdeasFromService() throws Exception {
        when(ideaService.getIdeas())
                .thenReturn(List.of(idea(1L, "First", 3), idea(2L, "Second", 1)));

        mvc.perform(get("/api/ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("First"))
                .andExpect(jsonPath("$[0].votes").value(3))
                .andExpect(jsonPath("$[1].title").value("Second"));
    }

    @Test
    void create_returns201WithSavedIdea() throws Exception {
        when(ideaService.createIdea(any(Idea.class))).thenReturn(idea(10L, "Ship it", 0));

        mvc.perform(
                        post("/api/ideas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"Ship it\",\"description\":\"soon\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Ship it"));
    }

    @Test
    void create_rejectsBlankTitle() throws Exception {
        mvc.perform(
                        post("/api/ideas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ideaService);
    }

    @Test
    void create_rejectsTitleOver200Chars() throws Exception {
        String longTitle = "x".repeat(201);

        mvc.perform(
                        post("/api/ideas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"" + longTitle + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ideaService);
    }

    @Test
    void vote_returnsUpdatedIdea() throws Exception {
        when(ideaService.vote(4L)).thenReturn(idea(4L, "Voted", 5));

        mvc.perform(post("/api/ideas/4/vote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.votes").value(5));

        verify(ideaService).vote(4L);
    }

    @Test
    void vote_onlyAcceptsPost() throws Exception {
        mvc.perform(get("/api/ideas/4/vote")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(ideaService);
    }

    private static Idea idea(Long id, String title, int votes) {
        Idea idea = new Idea();
        ReflectionTestUtils.setField(idea, "id", id);
        idea.setTitle(title);
        idea.setVotes(votes);
        return idea;
    }
}
