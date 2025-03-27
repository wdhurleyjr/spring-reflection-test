

// 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

package com.reflectiontest.springReflectionTest.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.tinylog.Logger;

import java.util.*;

public class ChatGPTUtil {
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private ChatGPTUtil() {
        // Private constructor to prevent instantiation
    }

    public static List<Map<String, Object>> generateTestCases(String methodName, String parameters, String returnType, String description, Map<String, String> modelStructure) {
        String apiKey = "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";

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing OpenAI API Key! Set OPENAI_API_KEY environment variable.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // Request body setup
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a Java testing assistant."),
                Map.of("role", "system", "content", "Your task is to generate structured JSON test cases for a given method."),
                Map.of("role", "system", "content", "Return JSON output **without** markdown (```json ... ```)."),
                Map.of("role", "system", "content", "Respond with a **raw JSON array** containing test cases."),
                Map.of("role", "user", "content",
                        "Generate JSON test cases for the following method:\n" +
                                "Method Name: " + methodName + "\n" +
                                "Parameters: " + parameters + "\n" +
                                "Return Type: " + returnType + "\n" +
                                "Model Structure: " + modelStructure + "\n" +  // 🔥 Pass model structure
                                "Respond with a JSON array of test cases, each containing 'inputJson' and 'expectedJson'."
                )
        ));

        Logger.info("🔄 Sending request to ChatGPT for test generation: {}", methodName);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.exchange(API_URL, HttpMethod.POST, requestEntity, Map.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                Logger.error("❌ Unexpected response from ChatGPT API: Status {}, Body {}", response.getStatusCode(), response.getBody());
                throw new IllegalStateException("Unexpected response from ChatGPT API");
            }

            Map<String, Object> responseBody = response.getBody();
            Logger.debug("📝 ChatGPT API response body: {}", responseBody);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");

            if (choices == null || choices.isEmpty()) {
                Logger.error("⚠️ No choices found in ChatGPT response");
                throw new IllegalStateException("No choices found in ChatGPT response");
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            String content = (String) message.get("content");

            if (content == null || content.isBlank()) {
                Logger.error("⚠️ ChatGPT returned an empty response");
                throw new IllegalStateException("ChatGPT returned an empty response");
            }

            Logger.info("✅ Successfully retrieved AI-generated test cases from ChatGPT");
            return parseJsonTestCases(cleanJsonResponse(content));

        } catch (Exception e) {
            Logger.error(e, "❌ Failed to get AI-generated tests from ChatGPT API: {}", e.getMessage());
            throw new RuntimeException("Failed to get AI-generated tests from ChatGPT API: " + e.getMessage(), e);
        }
    }

    /**
     * Parses JSON test cases from ChatGPT response.
     */
    private static List<Map<String, Object>> parseJsonTestCases(String jsonResponse) {
        try {
            JSONArray jsonArray = new JSONArray(jsonResponse);
            List<Map<String, Object>> testCases = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                testCases.add(jsonToMap(obj));
            }

            return testCases;
        } catch (Exception e) {
            Logger.error(e, "❌ Failed to parse JSON test cases from ChatGPT response");
            throw new RuntimeException("Failed to parse JSON test cases from ChatGPT response: " + e.getMessage(), e);
        }
    }

    /**
     * Converts JSONObject to Java Map.
     */
    private static Map<String, Object> jsonToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = jsonObject.keys();

        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonObject.get(key));
        }
        return map;
    }

    /**
     * Strips unwanted Markdown and cleans the JSON response.
     */
    private static String cleanJsonResponse(String response) {
        return response.replaceAll("```json", "")  // Remove opening markdown
                .replaceAll("```", "")      // Remove closing markdown
                .trim();
    }
}
