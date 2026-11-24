package com.brian.starter.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brian.starter.exception.IdeaNotFoundException;
import com.brian.starter.model.Idea;
import com.brian.starter.model.IdeaStatus;
import com.brian.starter.service.IdeaService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @Test
    void create_validationFailureReturnsProblemWithFieldErrors() throws Exception {
        mvc.perform(
                        post("/api/ideas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void create_malformedJsonReturns400Problem() throws Exception {
        mvc.perform(post("/api/ideas").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ideaService);
    }

    @Test
    void vote_unknownIdReturns404Problem() throws Exception {
        when(ideaService.vote(99L)).thenThrow(new IdeaNotFoundException(99L));

        mvc.perform(post("/api/ideas/99/vote"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Idea not found"))
                .andExpect(jsonPath("$.detail").value("Idea 99 not found."));
    }

    @Test
    void vote_nonNumericIdReturns400() throws Exception {
        mvc.perform(post("/api/ideas/abc/vote")).andExpect(status().isBadRequest());

        verifyNoInteractions(ideaService);
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() throws Exception {
        when(ideaService.getIdeas()).thenThrow(new RuntimeException("db password is hunter2"));

        mvc.perform(get("/api/ideas"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void list_includesStatusAndAllowedActions() throws Exception {
        when(ideaService.getIdeas()).thenReturn(List.of(idea(1L, "Open idea", 0)));

        mvc.perform(get("/api/ideas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(
                        jsonPath("$[0].allowedStatuses", containsInAnyOrder("PLANNED", "DONE", "CLOSED")))
                .andExpect(jsonPath("$[0].votable").value(true));
    }

    @Test
    void changeStatus_returnsUpdatedIdea() throws Exception {
        when(ideaService.changeStatus(4L, IdeaStatus.PLANNED))
                .thenReturn(idea(4L, "Planned idea", 2, IdeaStatus.PLANNED));

        mvc.perform(
                        post("/api/ideas/4/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"PLANNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.allowedStatuses", containsInAnyOrder("DONE", "CLOSED")));

        verify(ideaService).changeStatus(4L, IdeaStatus.PLANNED);
    }

    @Test
    void changeStatus_missingStatusReturns400WithFieldError() throws Exception {
        mvc.perform(
                        post("/api/ideas/4/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));

        verifyNoInteractions(ideaService);
    }

    @Test
    void changeStatus_unknownStatusValueReturns400() throws Exception {
        mvc.perform(
                        post("/api/ideas/4/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(ideaService);
    }

    @Test
    void changeStatus_unknownIdReturns404() throws Exception {
        when(ideaService.changeStatus(99L, IdeaStatus.DONE))
                .thenThrow(new IdeaNotFoundException(99L));

        mvc.perform(
                        post("/api/ideas/99/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowedStatuses_returnsTransitionMap() throws Exception {
        mvc.perform(get("/api/ideas/allowedStatuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.OPEN", containsInAnyOrder("PLANNED", "DONE", "CLOSED")))
                .andExpect(jsonPath("$.PLANNED", containsInAnyOrder("DONE", "CLOSED")))
                .andExpect(jsonPath("$.DONE", hasSize(0)))
                .andExpect(jsonPath("$.CLOSED", hasSize(0)));
    }

    private static Idea idea(Long id, String title, int votes) {
        return idea(id, title, votes, IdeaStatus.OPEN);
    }

    private static Idea idea(Long id, String title, int votes, IdeaStatus status) {
        Idea idea = new Idea();
        ReflectionTestUtils.setField(idea, "id", id);
        idea.setTitle(title);
        idea.setVotes(votes);
        idea.setStatus(status);
        return idea;
    }
}
