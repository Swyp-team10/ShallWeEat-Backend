package org.example.shallweeatbackend.repository;

import org.example.shallweeatbackend.entity.TeamBoard;
import org.example.shallweeatbackend.entity.TeamMember;
import org.example.shallweeatbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByUserUserId(Long userId);

    boolean existsByTeamBoardAndUser(TeamBoard teamBoard, User user);

    Optional<TeamMember> findByUserAndTeamBoard(User user, TeamBoard teamBoard);

    int countByTeamBoard(TeamBoard teamBoard);
}
