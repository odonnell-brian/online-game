package com.example.interview.controller;

import com.example.interview.repository.ItemRepository;
import com.example.interview.model.Item;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** JSON API in case the interviewer wants a REST/SPA-style design. */
@RestController
@RequestMapping("/api/items")
public class ItemApiController {
    private final ItemRepository repo;

    public ItemApiController(ItemRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Item> list() {
        return repo.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@Valid @RequestBody Item item) {
        return repo.save(item);
    }
}
