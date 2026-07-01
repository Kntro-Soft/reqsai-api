package com.kntro.reqsai.search.interfaces.rest.controllers;

import com.kntro.reqsai.search.application.GlobalSearchService;
import com.kntro.reqsai.search.interfaces.rest.dto.response.SearchHitResponse;
import com.kntro.reqsai.search.interfaces.rest.mappers.response.SearchHitResponseMapper;
import com.kntro.reqsai.search.interfaces.rest.swagger.SearchController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Implementation of the {@link SearchController} API contract. */
@RestController
@RequiredArgsConstructor
public class SearchControllerImpl implements SearchController {

    private final GlobalSearchService globalSearch;

    @Override
    public ResponseEntity<List<SearchHitResponse>> search(String q, int limit, Authentication authentication) {
        UUID callerId = UUID.fromString(authentication.getName());
        List<SearchHitResponse> hits = globalSearch.search(q, limit, callerId).stream()
                .map(SearchHitResponseMapper::toResponse)
                .toList();
        return ResponseEntity.ok(hits);
    }
}
