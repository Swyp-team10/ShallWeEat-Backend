package org.example.shallweeatbackend.service;

import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.dto.CategoryMenuDTO;
import org.example.shallweeatbackend.dto.PersonalBoardDTO;
import org.example.shallweeatbackend.dto.RecommendMenuDTO;
import org.example.shallweeatbackend.dto.RecommendOptionsDTO;
import org.example.shallweeatbackend.entity.Menu;
import org.example.shallweeatbackend.entity.PersonalBoard;
import org.example.shallweeatbackend.entity.PersonalBoardMenu;
import org.example.shallweeatbackend.entity.User;
import org.example.shallweeatbackend.exception.PersonalBoardNotFoundException;
import org.example.shallweeatbackend.exception.UserNotFoundException;
import org.example.shallweeatbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PersonalBoardService {

    private final PersonalBoardRepository personalBoardRepository;
    private final MenuRepository menuRepository;
    private final UserRepository userRepository;
    private final PersonalBoardMenuRepository personalBoardMenuRepository;

    public PersonalBoardDTO createPersonalBoard(String providerId, String name) {
        User user = userRepository.findByProviderId(providerId);
        if (user == null) {
            throw new UserNotFoundException("유저를 찾을 수 없습니다.");
        }

        PersonalBoard personalBoard = new PersonalBoard();
        personalBoard.setUser(user);
        personalBoard.setName(name);
        PersonalBoard savedPersonalBoard = personalBoardRepository.save(personalBoard);
        return convertToDTO(savedPersonalBoard);
    }

    public List<PersonalBoardDTO> getPersonalBoardsByUserProviderId(String providerId) {
        User user = userRepository.findByProviderId(providerId);
        if (user == null) {
            throw new UserNotFoundException("유저를 찾을 수 없습니다.");
        }

        return personalBoardRepository.findByUser(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public PersonalBoardDTO updatePersonalBoard(Long id, String name) {
        PersonalBoard personalBoard = personalBoardRepository.findById(id)
                .orElseThrow(() -> new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다."));
        personalBoard.setName(name);
        PersonalBoard updatedPersonalBoard = personalBoardRepository.save(personalBoard);
        return convertToDTO(updatedPersonalBoard);
    }

    public void deletePersonalBoard(Long id) {
        if (personalBoardRepository.existsById(id)) {
            personalBoardRepository.deleteById(id);
        } else {
            throw new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다. (메뉴판 ID: " + id + ")");
        }
    }

    public RecommendMenuDTO getMenuDetails(Long personalBoardId, Long menuId) {
        boolean existsPersonalBoard = personalBoardRepository.existsById(personalBoardId);
        if (!existsPersonalBoard) {
            throw new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다.");
        }

        Optional<Menu> optionalMenu = menuRepository.findByMenuIdWithTags(menuId);
        Menu menu = optionalMenu.orElseThrow(() -> new PersonalBoardNotFoundException("메뉴를 찾을 수 없습니다."));

        boolean existsPersonalBoardMenu = personalBoardMenuRepository.existsByPersonalBoardIdAndMenuId(personalBoardId, menuId);
        if (!existsPersonalBoardMenu) {
            throw new PersonalBoardNotFoundException("해당 메뉴가 메뉴판에 없습니다.");
        }

        return convertToRecommendMenuDTO(menu);
    }

    public List<CategoryMenuDTO> recommendMenus(Long personalBoardId, RecommendOptionsDTO options) {
        // 개인 메뉴판 존재 여부 확인
        PersonalBoard personalBoard = personalBoardRepository.findById(personalBoardId)
                .orElseThrow(() -> new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다."));

        // 모든 메뉴 가져오기
        List<Menu> allMenus = menuRepository.findAllWithTags();

        // 추천 메뉴 필터링
        List<RecommendMenuDTO> recommendedMenus = allMenus.stream()
                .filter(menu -> options.getTasteOptions().contains("ALL") || containsAny(convertToList(menu.getTasteOptions()), options.getTasteOptions()))
                .filter(menu -> options.getCarbOptions().contains("ALL") || containsAny(convertToList(menu.getCarbOptions()), options.getCarbOptions()))
                .filter(menu -> options.getWeatherOptions().contains("ALL") || containsAny(convertToList(menu.getWeatherOptions()), options.getWeatherOptions()))
                .filter(menu -> options.getCategoryOptions().contains("ALL") || containsAny(convertToList(menu.getCategoryOptions()), options.getCategoryOptions()))
                .map(this::convertToRecommendMenuDTO)
                .collect(Collectors.toList());

        // 기존 개인 메뉴판의 메뉴들 삭제
        personalBoardMenuRepository.deleteAllByPersonalBoard(personalBoard);

        // 새로운 추천 메뉴 저장
        List<PersonalBoardMenu> personalBoardMenus = recommendedMenus.stream().map(recommendMenuDTO -> {
            Menu menu = allMenus.stream()
                    .filter(m -> m.getMenuId().equals(recommendMenuDTO.getMenuId()))
                    .findFirst()
                    .orElseThrow(() -> new PersonalBoardNotFoundException("메뉴를 찾을 수 없습니다."));

            PersonalBoardMenu personalBoardMenu = new PersonalBoardMenu();
            personalBoardMenu.setPersonalBoard(personalBoard);
            personalBoardMenu.setMenu(menu);
            return personalBoardMenu;
        }).collect(Collectors.toList());

        personalBoardMenuRepository.saveAll(personalBoardMenus);

        // 카테고리별로 그룹화
        Map<String, List<CategoryMenuDTO.MenuDTO>> categorizedMenus = recommendedMenus.stream()
                .collect(Collectors.groupingBy(
                        RecommendMenuDTO::getCategoryOptions,
                        Collectors.mapping(recommendMenuDTO -> {
                            CategoryMenuDTO.MenuDTO menuDTO = new CategoryMenuDTO.MenuDTO();
                            menuDTO.setMenuId(recommendMenuDTO.getMenuId());
                            menuDTO.setImageUrl(recommendMenuDTO.getImageUrl());
                            menuDTO.setMenuName(recommendMenuDTO.getMenuName());
                            menuDTO.setTags(recommendMenuDTO.getTags());
                            return menuDTO;
                        }, Collectors.toList())
                ));

        // 카테고리 순서 지정
        List<String> categoryOrder = Arrays.asList("한식", "중식", "일식", "양식", "인도/베트남/태국", "멕시코", "육류/해산물");

        // 카테고리 순서에 따라 정렬하여 반환
        return categoryOrder.stream()
                .filter(categorizedMenus::containsKey)
                .map(category -> {
                    CategoryMenuDTO categoryMenuDTO = new CategoryMenuDTO();
                    categoryMenuDTO.setCategory(category);
                    categoryMenuDTO.setMenu(categorizedMenus.get(category));
                    return categoryMenuDTO;
                }).collect(Collectors.toList());
    }

    public List<RecommendMenuDTO> getMenusByPersonalBoardId(Long personalBoardId) {
        // 개인 메뉴판 존재 여부 확인
        boolean exists = personalBoardRepository.existsById(personalBoardId);
        if (!exists) {
            throw new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다.");
        }

        // 개인 메뉴판에 담긴 메뉴 조회
        List<PersonalBoardMenu> personalBoardMenus = personalBoardMenuRepository.findAllByPersonalBoardId(personalBoardId);

        return personalBoardMenus.stream()
                .map(pbMenu -> convertToRecommendMenuDTO(pbMenu.getMenu()))
                .collect(Collectors.toList());
    }

    public List<CategoryMenuDTO> getAllMenusByCategory(Long personalBoardId) {
        boolean exists = personalBoardRepository.existsById(personalBoardId);
        if (!exists) {
            throw new PersonalBoardNotFoundException("메뉴판을 찾을 수 없습니다.");
        }

        List<PersonalBoardMenu> personalBoardMenus = personalBoardMenuRepository.findAllByPersonalBoardId(personalBoardId);

        Map<String, List<CategoryMenuDTO.MenuDTO>> categorizedMenus = personalBoardMenus.stream()
                .collect(Collectors.groupingBy(
                        pbMenu -> pbMenu.getMenu().getCategoryOptions(),
                        Collectors.mapping(pbMenu -> {
                            CategoryMenuDTO.MenuDTO menuDTO = new CategoryMenuDTO.MenuDTO();
                            menuDTO.setMenuId(pbMenu.getMenu().getMenuId());
                            menuDTO.setImageUrl(pbMenu.getMenu().getImageUrl());
                            menuDTO.setMenuName(pbMenu.getMenu().getMenuName());
                            menuDTO.setTags(pbMenu.getMenu().getMenuTags().stream()
                                    .map(menuTag -> menuTag.getTag().getName())
                                    .collect(Collectors.toList()));
                            return menuDTO;
                        }, Collectors.toList())
                ));

        // 카테고리 순서 지정
        List<String> categoryOrder = Arrays.asList("한식", "중식", "일식", "양식", "인도/베트남/태국", "멕시코", "육류/해산물");

        // 카테고리 순서에 따라 정렬하여 반환
        return categoryOrder.stream()
                .filter(categorizedMenus::containsKey)
                .map(category -> {
                    CategoryMenuDTO categoryMenuDTO = new CategoryMenuDTO();
                    categoryMenuDTO.setCategory(category);
                    categoryMenuDTO.setMenu(categorizedMenus.get(category));
                    return categoryMenuDTO;
                }).collect(Collectors.toList());
    }

    public List<CategoryMenuDTO> recommendMenusForGuest(RecommendOptionsDTO options) {
        // 모든 메뉴 가져오기
        List<Menu> allMenus = menuRepository.findAllWithTags();

        // 추천 메뉴 필터링
        List<RecommendMenuDTO> recommendedMenus = allMenus.stream()
                .filter(menu -> options.getTasteOptions().contains("ALL") || containsAny(convertToList(menu.getTasteOptions()), options.getTasteOptions()))
                .filter(menu -> options.getCarbOptions().contains("ALL") || containsAny(convertToList(menu.getCarbOptions()), options.getCarbOptions()))
                .filter(menu -> options.getWeatherOptions().contains("ALL") || containsAny(convertToList(menu.getWeatherOptions()), options.getWeatherOptions()))
                .filter(menu -> options.getCategoryOptions().contains("ALL") || containsAny(convertToList(menu.getCategoryOptions()), options.getCategoryOptions()))
                .map(this::convertToRecommendMenuDTO)
                .collect(Collectors.toList());

        // 카테고리별로 그룹화
        Map<String, List<CategoryMenuDTO.MenuDTO>> categorizedMenus = recommendedMenus.stream()
                .collect(Collectors.groupingBy(
                        RecommendMenuDTO::getCategoryOptions,
                        Collectors.mapping(recommendMenuDTO -> {
                            CategoryMenuDTO.MenuDTO menuDTO = new CategoryMenuDTO.MenuDTO();
                            menuDTO.setMenuId(recommendMenuDTO.getMenuId());
                            menuDTO.setImageUrl(recommendMenuDTO.getImageUrl());
                            menuDTO.setMenuName(recommendMenuDTO.getMenuName());
                            menuDTO.setTags(recommendMenuDTO.getTags());
                            return menuDTO;
                        }, Collectors.toList())
                ));

        // 카테고리 순서 지정
        List<String> categoryOrder = Arrays.asList("한식", "중식", "일식", "양식", "인도/베트남/태국", "멕시코", "육류/해산물");

        // 카테고리 순서에 따라 정렬하여 반환
        return categoryOrder.stream()
                .filter(categorizedMenus::containsKey)
                .map(category -> {
                    CategoryMenuDTO categoryMenuDTO = new CategoryMenuDTO();
                    categoryMenuDTO.setCategory(category);
                    categoryMenuDTO.setMenu(categorizedMenus.get(category));
                    return categoryMenuDTO;
                }).collect(Collectors.toList());
    }

    /**
     * Entity -> DTO 변환 메서드
     */
    private PersonalBoardDTO convertToDTO(PersonalBoard personalBoard) {
        PersonalBoardDTO dto = new PersonalBoardDTO();
        dto.setPersonalBoardId(personalBoard.getPersonalBoardId());
        dto.setName(personalBoard.getName());
        dto.setUserName(personalBoard.getUser().getName());
        dto.setUserEmail(personalBoard.getUser().getEmail());
        dto.setCreatedDate(personalBoard.getCreatedDate());
        dto.setModifiedDate(personalBoard.getModifiedDate());
        return dto;
    }

    private List<String> convertToList(String commaSeparatedString) {
        return Arrays.asList(commaSeparatedString.split(","));
    }

    private boolean containsAny(List<String> source, List<String> target) {
        for (String item : target) {
            if (source.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private RecommendMenuDTO convertToRecommendMenuDTO(Menu menu) {
        RecommendMenuDTO dto = new RecommendMenuDTO();
        dto.setMenuId(menu.getMenuId());
        dto.setImageUrl(menu.getImageUrl());
        dto.setMenuName(menu.getMenuName());
        dto.setCategoryOptions(menu.getCategoryOptions());
        List<String> tags = menu.getMenuTags().stream()
                .map(menuTag -> menuTag.getTag().getName())
                .collect(Collectors.toList());
        dto.setTags(tags);
        return dto;
    }
}
