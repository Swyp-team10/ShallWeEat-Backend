package org.example.shallweeatbackend.service;

import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.dto.CountVotedMembersNumDTO;
import org.example.shallweeatbackend.dto.VoteDTO;
import org.example.shallweeatbackend.entity.*;
import org.example.shallweeatbackend.exception.*;
import org.example.shallweeatbackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository voteRepository;
    private final TeamBoardRepository teamBoardRepository;
    private final TeamBoardMenuRepository teamBoardMenuRepository;
    private final MenuRepository menuRepository;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;

    private static final int MAX_VOTES_PER_USER = 3;

    public List<VoteDTO> createVotes(String providerId, Long teamBoardId, List<Long> menuIds) {
        User user = userRepository.findByProviderId(providerId);
        TeamBoard teamBoard = teamBoardRepository.findById(teamBoardId)
                .orElseThrow(() -> new TeamBoardNotFoundException("팀 보드를 찾을 수 없습니다."));

        if (!teamMemberRepository.existsByTeamBoardAndUser(teamBoard, user) && !teamBoard.getUser().equals(user)) {
            throw new UnauthorizedVoteException("팀 메뉴판에 초대된 사람들만 투표할 수 있습니다.");
        }

        List<VoteDTO> voteDTOs = new ArrayList<>();

        for (Long menuId : menuIds) {
            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new MenuNotFoundException("메뉴를 찾을 수 없습니다."));

            long voteCount = voteRepository.countByUserUserIdAndTeamBoardTeamBoardId(user.getUserId(), teamBoardId);
            if (voteCount >= MAX_VOTES_PER_USER) {
                throw new VoteLimitExceededException("한 사람당 최대 3개의 메뉴에만 투표할 수 있습니다.");
            }

            // 이미 투표한 메뉴인지 확인
            boolean alreadyVoted = voteRepository.existsByUserUserIdAndTeamBoardTeamBoardIdAndMenuMenuId(user.getUserId(), teamBoardId, menuId);
            if (alreadyVoted) {
                throw new DuplicateVoteException("이미 이 메뉴에 투표하셨습니다.");
            }

            // teamBoard와 menu에 대해 teamBoardMenuId가 가장 낮은 항목 찾기
            TeamBoardMenu teamBoardMenu = teamBoardMenuRepository.findByTeamBoardAndMenu(teamBoard, menu)
                    .stream()
                    .min(Comparator.comparing(TeamBoardMenu::getTeamBoardMenuId))
                    .orElseThrow(() -> new TeamBoardMenuNotFoundException("팀 보드 메뉴를 찾을 수 없습니다."));

            Vote vote = new Vote();
            vote.setUser(user);
            vote.setTeamBoard(teamBoard);
            vote.setMenu(menu);
            vote.setTeamBoardMenu(teamBoardMenu);

            Vote savedVote = voteRepository.save(vote);
            voteDTOs.add(convertToDTO(savedVote));
        }

        return voteDTOs;
    }

    public List<VoteDTO> updateVotes(String providerId, Long teamBoardId, List<Long> menuIds) {
        User user = userRepository.findByProviderId(providerId);
        TeamBoard teamBoard = teamBoardRepository.findById(teamBoardId)
                .orElseThrow(() -> new TeamBoardNotFoundException("팀 보드를 찾을 수 없습니다."));

        // 기존 투표 삭제
        List<Vote> existingVotes = voteRepository.findByUserUserIdAndTeamBoardTeamBoardId(user.getUserId(), teamBoardId);
        voteRepository.deleteAll(existingVotes);

        // 새로운 투표 생성
        List<VoteDTO> updatedVoteDTOs = new ArrayList<>();
        for (Long menuId : menuIds) {
            Menu menu = menuRepository.findById(menuId)
                    .orElseThrow(() -> new MenuNotFoundException("메뉴를 찾을 수 없습니다."));

            TeamBoardMenu teamBoardMenu = teamBoardMenuRepository.findByTeamBoardAndMenu(teamBoard, menu)
                    .stream()
                    .min(Comparator.comparing(TeamBoardMenu::getTeamBoardMenuId))
                    .orElseThrow(() -> new TeamBoardMenuNotFoundException("팀 보드 메뉴를 찾을 수 없습니다."));

            Vote vote = new Vote();
            vote.setUser(user);
            vote.setTeamBoard(teamBoard);
            vote.setMenu(menu);
            vote.setTeamBoardMenu(teamBoardMenu);

            Vote savedVote = voteRepository.save(vote);
            updatedVoteDTOs.add(convertToDTO(savedVote));
        }

        return updatedVoteDTOs;
    }

    public Map<String, Object> getVoteResults(Long teamBoardId, String providerId) {
        TeamBoard teamBoard = teamBoardRepository.findById(teamBoardId)
                .orElseThrow(() -> new TeamBoardNotFoundException("팀 보드를 찾을 수 없습니다."));

        User user = userRepository.findByProviderId(providerId);
        if (user == null) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }

        List<Vote> votes = voteRepository.findByTeamBoardTeamBoardId(teamBoardId);

        if (votes.isEmpty()) {
            throw new VoteNotFoundException("투표를 찾을 수 없습니다.");
        }

        Map<Long, Long> menuVoteCounts = votes.stream()
                .collect(Collectors.groupingBy(vote -> vote.getMenu().getMenuId(), Collectors.counting()));

        List<Map<String, Object>> voteList = menuVoteCounts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey)) // 득표수에 따라 내림차순 정렬, 득표수가 같으면 menuId로 오름차순 정렬
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("menuId", entry.getKey());
                    map.put("menuName", menuRepository.findById(entry.getKey()).orElseThrow().getMenuName());
                    map.put("voteValue", entry.getValue());
                    return map;
                })
                .collect(Collectors.toList());


        boolean isVote = votes.stream()
                .anyMatch(vote -> vote.getUser().equals(user));

        Map<String, Object> result = new HashMap<>();
        result.put("teamName", teamBoard.getTeamName());
        result.put("votes", voteList);
        result.put("voteDate", votes.get(0).getCreatedDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        result.put("isVote", isVote);

        return result;
    }

    // 해당 게시판에 투표 참여한 인원 수 조회
    public CountVotedMembersNumDTO getTeamBoardVotedMembers(Long teamBoardId){
        Long votedUserCount = voteRepository.countDistinctVotedUsersByTeamBoardId(teamBoardId);
        Integer teamMembersNum = teamBoardRepository.findTeamMembersNumByTeamBoardId(teamBoardId);
        return new CountVotedMembersNumDTO(votedUserCount, teamMembersNum);
    }

    public void deleteVote(Long voteId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new VoteNotFoundException("투표를 찾을 수 없습니다."));
        voteRepository.delete(vote);
    }

    private VoteDTO convertToDTO(Vote vote) {
        VoteDTO dto = new VoteDTO();
        dto.setVoteId(vote.getVoteId());
        dto.setTeamBoardId(vote.getTeamBoard().getTeamBoardId());
        dto.setMenuId(vote.getMenu().getMenuId());
        dto.setUserId(vote.getUser().getUserId());
        dto.setTeamBoardMenuId(vote.getTeamBoardMenu().getTeamBoardMenuId());
        dto.setCreatedDate(vote.getCreatedDate());
        dto.setMenuName(vote.getMenu().getMenuName());
        return dto;
    }
}