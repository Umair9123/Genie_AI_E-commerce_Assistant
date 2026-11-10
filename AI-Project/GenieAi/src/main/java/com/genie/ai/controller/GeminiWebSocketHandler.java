package com.genie.ai.controller;

import com.genie.ai.entity.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.genie.ai.repo.ProductRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GeminiWebSocketHandler extends TextWebSocketHandler {


    @Value("${gemini.api.key}")
    private String apiKey;



    @Autowired
    private ProductRepository productRepository;

    //private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";
    private final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=";


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws IOException {

        String userInput = message.getPayload().trim();
        String normalizedInput = userInput.toLowerCase();

        System.out.println("Original User Input: " + userInput);

        String response;

        if (isEcommerceQuery(normalizedInput)) {
            response = handleEcommerceQuery(normalizedInput);
        } else {
            response = callGeminiForAnswer(userInput);
        }

        session.sendMessage(new TextMessage(response));
    }

    private boolean isEcommerceQuery(String userInput) {
        String[] productCategories = {"ac", "refrigerator", "fridge", "washing machine", "tv", "television"};

        for (String category : productCategories) {
            if (userInput.contains(category) && (userInput.contains("under") || userInput.contains("below"))) {
                return true;
            }
        }
        return false;
    }

    private String handleEcommerceQuery(String userInput) {
        try {
            // Categories to check
            String[] productCategories = {"ac", "refrigerator", "fridge", "washing machine", "tv", "television"};
            String detectedCategory = null;

            for (String category : productCategories) {
                if (userInput.contains(category)) {
                    if (category.equals("fridge")) detectedCategory = "Refrigerator";
                    else if (category.equals("tv") || category.equals("television")) detectedCategory = "Television";
                    else detectedCategory = capitalize(category);
                    break;
                }
            }

            if (detectedCategory == null) {
                return "Sorry, I couldn't identify the product category.";
            }

            // Extract price
            Pattern pricePattern = Pattern.compile("(under|below)\\s*(\\d+)");
            Matcher matcher = pricePattern.matcher(userInput);
            double maxPrice = 50000;  // Default if not found

            if (matcher.find()) {
                maxPrice = Double.parseDouble(matcher.group(2));
            }

            // Query DB for correct category and price
            List<Product> products = productRepository.findByCategoryAndPriceLessThan(detectedCategory, maxPrice);

            System.out.println(products);
            if (products.isEmpty()) {
                return "No " + detectedCategory + " found under ₹" + maxPrice;
            }

            // Correctly format the response
            StringBuilder response = new StringBuilder("Here are some " + detectedCategory + "s under ₹" + maxPrice + ":");
            for (Product product : products) {
                response.append(" || ").append(product.getName())
                        .append(" (₹").append(product.getPrice()).append(")");
            }
            System.out.println(response);
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();  // For debugging
            return "Error processing your request.";
        }
    }

    private String capitalize(String text) {
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }


    private String callGeminiForAnswer(String userInput) {

        try {
            RestTemplate restTemplate = new RestTemplate();

            // Get current date and time
            String currentDateTime = java.time.LocalDateTime.now(
                    java.time.ZoneId.of("Asia/Kolkata")
            ).format(
                    java.time.format.DateTimeFormatter.ofPattern(
                            "EEEE, dd MMMM yyyy, hh:mm a"
                    )
            );

            // Create prompt
            String prompt =
                    "You are Gemini, a helpful AI assistant.\n\n" +
                            "CURRENT DATE AND TIME: " + currentDateTime + "\n\n" +
                            "IMPORTANT RULES:\n" +
                            "1. Always use the provided current date and time.\n" +
                            "2. Never assume the date from your training data.\n" +
                            "3. If asked today's date, provide the exact date above.\n" +
                            "4. If asked current time, use the time above.\n" +
                            "5. Answer naturally and accurately.\n\n" +
                            "USER QUESTION: " + userInput;

            // Create JSON payload
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", Arrays.asList(part));

            Map<String, Object> payload = new HashMap<>();
            payload.put("contents", Arrays.asList(content));

            // Convert Java object to JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            String jsonPayload = mapper.writeValueAsString(payload);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity =
                    new HttpEntity<>(jsonPayload, headers);

            // Call Gemini API
            String url = GEMINI_URL + apiKey;

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("Current Date Sent: " + currentDateTime);
            System.out.println("Gemini Response: " + response.getBody());

            return response.getBody();

        } catch (Exception e) {

            System.err.println("Gemini API Error: " + e.getMessage());
            e.printStackTrace();

            return "{\"error\":\"API call failed\"}";
        }
    }



}
