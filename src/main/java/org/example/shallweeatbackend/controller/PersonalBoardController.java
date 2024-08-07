package org.example.shallweeatbackend.controller;

import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.dto.*;
import org.example.shallweeatbackend.exception.PersonalBoardNotFoundException;
import org.example.shallweeatbackend.exception.UserNotFoundException;
import org.example.shallweeatbackend.service.PersonalBoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/personalboards")
@RequiredArgsConstructor
public class PersonalBoardController {

    private final PersonalBoardService personalBoardService;

    @PostMapping
    public PersonalBoardDTO createPersonalBoard(@AuthenticationPrincipal CustomOAuth2User principal, @RequestParam String name) {
        return personalBoardService.createPersonalBoard(principal.getProviderId(), name);
    }

    @GetMapping
    public List<PersonalBoardDTO> getPersonalBoardsByUser(@AuthenticationPrincipal CustomOAuth2User principal) {
        return personalBoardService.getPersonalBoardsByUserProviderId(principal.getProviderId());
    }

    @GetMapping("/{personalBoardId}")
    public List<RecommendMenuDTO> getMenusByPersonalBoardId(@PathVariable Long personalBoardId) {
        return personalBoardService.getMenusByPersonalBoardId(personalBoardId);
    }

    @GetMapping("/{personalBoardId}/{menuId}")
    public RecommendMenuDTO getMenuDetails(@PathVariable Long personalBoardId, @PathVariable Long menuId) {
        return personalBoardService.getMenuDetails(personalBoardId, menuId);
    }

    @PatchMapping("/{personalBoardId}")
    public PersonalBoardDTO updatePersonalBoard(@PathVariable Long personalBoardId, @RequestParam String name) {
        return personalBoardService.updatePersonalBoard(personalBoardId, name);
    }

    @DeleteMapping("/{personalBoardId}")
    public ResponseEntity<Map<String, String>> deletePersonalBoard(@PathVariable Long personalBoardId) {
        personalBoardService.deletePersonalBoard(personalBoardId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "메뉴판이 성공적으로 삭제되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/{personalBoardId}/recommend")
    public List<CategoryMenuDTO> recommendMenus(@PathVariable Long personalBoardId, @RequestBody RecommendOptionsDTO options) {
        return personalBoardService.recommendMenus(personalBoardId, options);
    }

    @GetMapping("/{personalBoardId}/categories")
    public List<CategoryMenuDTO> getAllMenusByCategory(@PathVariable Long personalBoardId) {
        return personalBoardService.getAllMenusByCategory(personalBoardId);
    }

    @PostMapping("/guest/recommend")
    public List<CategoryMenuDTO> recommendMenusForGuest(@RequestBody RecommendOptionsDTO options) {
        return personalBoardService.recommendMenusForGuest(options);
    }

    // 예외 처리 핸들러 추가
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFoundException(UserNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PersonalBoardNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePersonalBoardNotFoundException(PersonalBoardNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }
}
