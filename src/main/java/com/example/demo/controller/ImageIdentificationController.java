package com.example.demo.controller;

import com.example.demo.model.IdentificationResult;
import com.example.demo.model.User;
import com.example.demo.repository.IdentificationResultRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ImageIdentificationController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.base.url}")
    private String geminiApiBaseUrl;

    @Value("${gemini.api.model}")
    private String geminiApiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    
    @Autowired
    private IdentificationResultRepository identificationResultRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    // List of model names to try in order if primary fails
    private static final String[] FALLBACK_MODELS = {
        "gemini-2.5-pro",
        "gemini-1.5-pro",
        "gemini-1.5-flash", 
        "gemini-pro",
        "gemini-pro-vision"
    };

    @GetMapping("/")
    public String home() {
        return "home";
    }
    
    @GetMapping("/identify")
    public String identify(Model model) {
        // Get authenticated user email
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        
        // Load all identification history for current user
        List<IdentificationResult> history = identificationResultRepository.findByUserIdOrderByCreatedAtDesc(userEmail);
        model.addAttribute("history", history);
        model.addAttribute("userEmail", userEmail);
        
        // Get user details for profile
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user != null) {
            model.addAttribute("userName", user.getName());
        }
        
        return "index";
    }
    
    @GetMapping("/about")
    public String about() {
        return "about";
    }
    
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                       @RequestParam(value = "logout", required = false) String logout,
                       Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "login";
    }
    
    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }
    
    @PostMapping("/signup")
    public String processSignup(@RequestParam("name") String name,
                                @RequestParam("email") String email,
                                @RequestParam("password") String password,
                                @RequestParam("confirmPassword") String confirmPassword,
                                Model model) {
        // Basic validation
        if (name == null || name.trim().isEmpty()) {
            model.addAttribute("error", "Name is required");
            return "signup";
        }
        
        if (email == null || email.trim().isEmpty()) {
            model.addAttribute("error", "Email is required");
            return "signup";
        }
        
        if (password == null || password.trim().isEmpty()) {
            model.addAttribute("error", "Password is required");
            return "signup";
        }
        
        if (password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters long");
            return "signup";
        }
        
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            return "signup";
        }
        
        // Check if email already exists
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Email already exists. Please use a different email or login.");
            return "signup";
        }
        
        // Hash password with BCrypt
        String hashedPassword = passwordEncoder.encode(password);
        
        // Create and save user
        User user = new User(name, email, hashedPassword);
        userRepository.save(user);
        
        model.addAttribute("success", "Account created successfully! Please login.");
        return "redirect:/login?signup=success";
    }

    @PostMapping("/upload")
    public String uploadImage(@RequestParam("image") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select an image file.");
                return "redirect:/identify";
            }

            // Convert image to base64
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = file.getContentType();

            // Prepare the request for Gemini API
            Map<String, Object> requestData = prepareRequestData(base64Image, mimeType);
            
            // Try the configured model first, then all fallback models
            List<String> modelsToTry = new ArrayList<>();
            modelsToTry.add(geminiApiModel);
            for (String fallback : FALLBACK_MODELS) {
                if (!modelsToTry.contains(fallback)) {
                    modelsToTry.add(fallback);
                }
            }
            
            String result = null;
            String lastError = null;
            
            // Try each model until one works
            for (String modelName : modelsToTry) {
                try {
                    result = callGeminiAPI(modelName, requestData);
                    if (result != null && !result.startsWith("Error") && !result.contains("Unable")) {
                        break; // Success!
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    // Continue to next model
                }
            }
            
            if (result != null && !result.startsWith("Error") && !result.contains("Unable")) {
                // Get authenticated user email
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                String userEmail = authentication.getName();
                
                // Save to MongoDB
                IdentificationResult identificationResult = new IdentificationResult(
                    result,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    userEmail
                );
                identificationResultRepository.save(identificationResult);
                
                redirectAttributes.addFlashAttribute("result", result);
                redirectAttributes.addFlashAttribute("success", "Image identified successfully!");
            } else {
                redirectAttributes.addFlashAttribute("error", "Failed to connect to Gemini API. Please check your API key and model configuration. Last error: " + 
                    (lastError != null ? lastError : "Model not available"));
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error processing image: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Redirect to GET /identify to prevent form resubmission on refresh
        return "redirect:/identify";
    }
    
    private Map<String, Object> prepareRequestData(String base64Image, String mimeType) {
        // Create parts array - image first, then text prompt
        Map<String, Object> imagePart = new HashMap<>();
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);
        imagePart.put("inline_data", inlineData);
        
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", "Analyze this image carefully and identify what it contains. Provide your response in EXACTLY ONE of these formats:\n\n" +
            "1. If it's an ANIMAL: Start your response with 'This is an animal: [Animal Name]' (e.g., 'This is an animal: Golden Retriever' or 'This is an animal: Bengal Tiger')\n" +
            "2. If it's a FLOWER: Start your response with 'This is a flower: [Flower Name]' (e.g., 'This is a flower: Rose' or 'This is a flower: Sunflower')\n" +
            "3. If it's an OBJECT: Start your response with 'This is an object: [Object Name]' (e.g., 'This is an object: Smartphone' or 'This is an object: Coffee Mug')\n" +
            "4. If it's something else: Start with 'This is: [Description]'\n\n" +
            "IMPORTANT: \n" +
            "- Be specific and accurate with the name\n" +
            "- If you can identify the exact breed, species, or type, include that in the name\n" +
            "- Start your response immediately with the identification format (e.g., 'This is an animal: [name]')\n" +
            "- Keep the response clear and concise");
        
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(imagePart);
        parts.add(textPart);
        
        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);
        
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(content);
        
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("contents", contents);
        return requestData;
    }
    
    private String callGeminiAPI(String modelName, Map<String, Object> requestData) {
        // Try v1beta first
        String[] apiVersions = {"v1beta", "v1"};
        String lastError = null;
        
        for (String apiVersion : apiVersions) {
            try {
                String url = String.format("%s/%s/models/%s:generateContent?key=%s", 
                    geminiApiBaseUrl, apiVersion, modelName, geminiApiKey);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestData, headers);
                
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                
                String result = extractResultFromResponse(response.getBody());
                if (result != null && !result.contains("Unable")) {
                    return result;
                }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                lastError = String.format("API v%s: %s", apiVersion, e.getMessage());
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    continue; // Try next version
                } else {
                    throw e; // Other errors should be thrown
                }
            }
        }
        
        throw new RuntimeException("Model " + modelName + " not found in any API version. " + lastError);
    }
    

    @SuppressWarnings("unchecked")
    private String extractResultFromResponse(Map<String, Object> response) {
        try {
            if (response == null || !response.containsKey("candidates")) {
                return "Unable to identify the image. Please try again.";
            }
            
            Object candidatesObj = response.get("candidates");
            if (candidatesObj instanceof java.util.List) {
                java.util.List<Map<String, Object>> candidates = (java.util.List<Map<String, Object>>) candidatesObj;
                if (!candidates.isEmpty()) {
                    Map<String, Object> candidate = candidates.get(0);
                    if (candidate.containsKey("content")) {
                        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                        if (content.containsKey("parts")) {
                            java.util.List<Map<String, Object>> parts = (java.util.List<Map<String, Object>>) content.get("parts");
                            if (!parts.isEmpty() && parts.get(0).containsKey("text")) {
                                return parts.get(0).get("text").toString();
                            }
                        }
                    }
                }
            }
            return "Unable to identify the image. Please try again.";
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }
}

