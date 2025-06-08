package io.github.alvinchiu.gstreamgate.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Home Controller for serving the React frontend
 */
@Controller
public class HomeController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String home() throws IOException {
        Resource resource = new ClassPathResource("static/index.html");
        if (resource.exists()) {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } else {
            return "<!DOCTYPE html><html><head><title>gStreamGate</title></head><body><h1>gStreamGate</h1><p>Frontend not found</p></body></html>";
        }
    }

    @GetMapping(value = {"/dashboard", "/proxies", "/settings", "/users"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String spa() throws IOException {
        return home(); // SPA routes should return the same index.html
    }
}