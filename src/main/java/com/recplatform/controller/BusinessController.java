package com.recplatform.controller;

import com.recplatform.model.BusinessDocument;
import com.recplatform.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final RankingService rankingService;

    @GetMapping("/search")
    public List<BusinessDocument> search(
            @RequestParam String category,
            @RequestParam(defaultValue = "20") int limit) {
        return rankingService.search(category, limit);
    }
}
