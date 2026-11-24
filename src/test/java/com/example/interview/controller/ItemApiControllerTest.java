package com.example.interview.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.interview.model.Item;
import com.example.interview.repository.ItemRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItemApiController.class)
class ItemApiControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private ItemRepository repo;

  @Test
  void list_returnsAllItems() throws Exception {
    when(repo.findAll()).thenReturn(List.of(item("One"), item("Two")));

    mvc.perform(get("/api/items"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].title").value("One"));
  }

  @Test
  void create_returns201WithSavedItem() throws Exception {
    when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

    mvc.perform(
            post("/api/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hello\",\"description\":\"World\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Hello"))
        .andExpect(jsonPath("$.description").value("World"));
  }

  @Test
  void create_rejectsMissingTitle() throws Exception {
    mvc.perform(
            post("/api/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(repo);
  }

  private static Item item(String title) {
    Item item = new Item();
    item.setTitle(title);
    return item;
  }
}
