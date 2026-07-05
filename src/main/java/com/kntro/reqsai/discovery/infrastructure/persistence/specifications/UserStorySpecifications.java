package com.kntro.reqsai.discovery.infrastructure.persistence.specifications;

import com.kntro.reqsai.discovery.application.query.StoryFilter;
import com.kntro.reqsai.discovery.domain.model.UserStory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Criteria-API {@link Specification} factory for the user-story backlog listing. Each optional
 * {@link StoryFilter} field contributes a predicate only when present, so a null filter simply adds no
 * clause — no untyped null parameters ever reach the database. Filtering runs server-side; the
 * {@code Pageable} handles sorting and paging.
 */
public final class UserStorySpecifications {

    private UserStorySpecifications() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** All stories of {@code projectId} matching the (possibly empty) {@code filter}. */
    public static Specification<UserStory> forProject(UUID projectId, StoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectId"), projectId));

            String search = filter.normalizedSearch();
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("role")), pattern),
                        cb.like(cb.lower(root.get("action")), pattern)));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.createdAfter() != null) {
                // inclusive lower bound
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdAfter()));
            }
            if (filter.createdBefore() != null) {
                // exclusive upper bound
                predicates.add(cb.lessThan(root.get("createdAt"), filter.createdBefore()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
