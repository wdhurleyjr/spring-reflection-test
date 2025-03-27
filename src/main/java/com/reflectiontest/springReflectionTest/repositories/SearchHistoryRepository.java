package com.reflectiontest.springReflectionTest.repositories;

import com.reflectiontest.springReflectionTest.annotations.MockReturn;
import com.reflectiontest.springReflectionTest.annotations.MockReturns;
import java.util.Map;
import java.util.HashMap;

/**
 * Repository for search history with mock return annotations
 */
public interface SearchHistoryRepository {
    /**
     * Saves a search term
     */
    @MockReturns({
            @MockReturn(inputJson = "\"Laptop\"", returnJson = ""),
            @MockReturn(inputJson = "\"Mouse\"", returnJson = "")
    })
    void saveSearch(String searchTerm);

    /**
     * Gets counts of all search terms
     */
    @MockReturns({
            @MockReturn(inputJson = "",
                    returnJson = "{\"Laptop\":5,\"Mouse\":3,\"Keyboard\":2}",
                    isDefault = true),
            @MockReturn(inputJson = "",
                    returnJson = "{\"Smartphone\":10,\"Tablet\":7}",
                    isDefault = false)
    })
    Map<String, Long> getSearchCounts();

    /**
     * Clears search history
     */
    @MockReturns({
            @MockReturn(inputJson = "", returnJson = "")
    })
    void clearHistory();
}
