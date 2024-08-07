package org.example.shallweeatbackend.controller;

import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.dto.*;
import org.example.shallweeatbackend.entity.TeamBoard;
import org.example.shallweeatbackend.service.TeamBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/teamboards")
@RequiredArgsConstructor
public class TeamBoardController {
    private TeamBoardService teamBoardService;

    @Autowired
    public TeamBoardController(TeamBoardService teamBoardService) {
        this.teamBoardService = teamBoardService;
    }

    // 팀 메뉴판 생성
    @PostMapping
    public TeamBoardDTO createTeamBoard(@AuthenticationPrincipal CustomOAuth2User principal, @RequestParam String teamName, @RequestParam Integer teamMembersNum, @RequestParam String teamBoardName) {
        return teamBoardService.createTeamBoard(principal.getProviderId(), teamName, teamMembersNum, teamBoardName);
    }

    // 특정 팀 메뉴판 조회
    @GetMapping("/{teamBoardId}")
    public ResponseEntity<OneTeamBoardListDTO> getTeamBoard(@AuthenticationPrincipal CustomOAuth2User principal, @PathVariable Long teamBoardId) {
        if (principal == null) {
            throw new IllegalStateException("사용자가 인증되지 않았습니다.");
        }
        OneTeamBoardListDTO oneteamBoardDTO = teamBoardService.getTeamBoard(principal.getProviderId(), teamBoardId);
        return ResponseEntity.ok(oneteamBoardDTO);
    }

    // 팀 메뉴판 수정
    @PatchMapping("/{teamBoardId}")
    public TeamBoardDTO updateTeamBoard(@PathVariable Long teamBoardId, @AuthenticationPrincipal CustomOAuth2User principal,
                                        @RequestBody TeamBoardRequest request) {
        return teamBoardService.updateTeamBoard(teamBoardId, principal.getProviderId(), request.getTeamName(), request.getTeamMembersNum(), request.getTeamBoardName());
    }

    // 팀 메뉴판 삭제
    @DeleteMapping("/{teamBoardId}")
    public ResponseEntity<Map<String, String>> deleteTeamBoard(@PathVariable Long teamBoardId) {
        teamBoardService.deleteTeamBoard(teamBoardId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "teamBoardId : " + teamBoardId + ", 팀 메뉴판이 성공적으로 삭제되었습니다.");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    // 사용자 별 팀 메뉴판 전체 목록 조회 (사용자 본인이 직접 생성했거나 팀원으로 참여하고 있는 경우)
    @GetMapping("/list")
    public ResponseEntity<List<TeamBoardListDTO>> getUserTeamBoards(@AuthenticationPrincipal CustomOAuth2User principal) {
        if (principal == null) {
            throw new IllegalStateException("사용자가 인증되지 않았습니다.");
        }
        List<TeamBoardListDTO> teamBoards = teamBoardService.getUserTeamBoards(principal.getProviderId());
        return ResponseEntity.ok(teamBoards);
    }

}
