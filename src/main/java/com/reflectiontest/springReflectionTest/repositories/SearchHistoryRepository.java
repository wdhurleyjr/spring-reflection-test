package com.reflectiontest.springReflectionTest.repositories;

import java.util.Map;

/**
 * Repository for search history
 */
public interface SearchHistoryRepository {
    /**
     * Saves a search term
     */
    void saveSearch(String searchTerm);

    /**
     * Gets counts of all search terms
     */
    Map<String, Long> getSearchCounts();

    /**
     * Clears search history
     */
    void clearHistory();
}
