package org.example.shallweeatbackend.service;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.entity.RefreshToken;
import org.example.shallweeatbackend.entity.User;
import org.example.shallweeatbackend.repository.UserRepository;
import org.example.shallweeatbackend.util.JWTUtil;
import org.example.shallweeatbackend.repository.RefreshTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 리프레시 토큰을 재발급하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
public class ReissueTokenService {

    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {
        // 클라이언트의 HttpServletRequest에서 refresh 토큰 추출
        String refreshToken = getRefreshTokenFromRequest(request);

        // 응답 메시지를 저장할 Map 객체 생성
        Map<String, String> responseBody = new HashMap<>();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // 추출된 refresh 토큰이 없는 경우, 클라이언트에게 400 응답 반환
        if (refreshToken == null) {
            responseBody.put("message", "리프레시 토큰이 필요합니다.");
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }

        try {
            // refresh 토큰 만료 여부 검증
            jwtUtil.isExpired(refreshToken);
        } catch (ExpiredJwtException e) {
            // refresh 토큰이 만료된 경우, 클라이언트에게 401 응답 반환
            responseBody.put("message", "리프레시 토큰이 만료되었습니다.");
            return new ResponseEntity<>(responseBody, HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            // 토큰 검증 중 다른 예외가 발생한 경우, 클라이언트에게 400 응답 반환
            responseBody.put("message", "유효하지 않은 토큰입니다.");
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }

        // DB에서 refresh 토큰의 존재 여부 확인
        Boolean isExist = refreshTokenRepository.existsByRefreshToken(refreshToken);
        if (!isExist) {
            // 존재하지 않는 경우, 클라이언트에게 400 응답 반환
            responseBody.put("message", "리프레시 토큰을 찾을 수 없습니다.");
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }

        // refresh 토큰 타입 검사
        String category = jwtUtil.getCategory(refreshToken);
        if (!category.equals("refresh")) {
            // 유효하지 않은 경우, 클라이언트에게 400 응답 반환
            responseBody.put("message", "유효하지 않은 토큰 유형입니다.");
            return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
        }

        // 새로운 access 토큰과 refresh 토큰을 발급하고, DB에 새 refresh 토큰 추가
        String providerId = jwtUtil.getProviderId(refreshToken);
        String role = jwtUtil.getRole(refreshToken);
        String newAccessToken = jwtUtil.createJwt("access", providerId, role, 1800000L); // 30분 (1800000ms)
        String newRefreshToken = jwtUtil.createJwt("refresh", providerId, role, 1209600000L); // 2주 (1209600000ms)
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
        addRefresh(providerId, newRefreshToken);

        // 응답 본문에 access 토큰 포함
        responseBody.put("access", newAccessToken);
        responseBody.put("message", "액세스 토큰과 리프레시 토큰이 재발급되었습니다.");

        // 새로운 refresh 토큰 쿠키로 전송
        response.addCookie(createCookie(newRefreshToken));

        // 클라이언트에게 200 OK 응답 반환
        return new ResponseEntity<>(responseBody, HttpStatus.OK);
    }

    // refresh 토큰 추출
    private String getRefreshTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // refresh 토큰 DB에 저장
    private void addRefresh(String providerId, String refreshToken) {
        // 현재 시간에 2주를 더하여 만료 시간 설정
        LocalDateTime expirationTime = LocalDateTime.now().plusWeeks(2);

        // user entity 조회
        User user = userRepository.findByProviderId(providerId);

        RefreshToken refreshEntity = new RefreshToken();
        refreshEntity.setUser(user);
        refreshEntity.setRefreshToken(refreshToken);
        refreshEntity.setExpirationTime(expirationTime);

        refreshTokenRepository.save(refreshEntity);
    }

    // 쿠키 생성
    private Cookie createCookie(String value) {
        Cookie cookie = new Cookie("refresh", value);
        cookie.setMaxAge(14 * 24 * 60 * 60); // 쿠키의 유효 기간 설정 (14일, 초 단위)
        cookie.setDomain("molip.site");
        cookie.setAttribute("SameSite", "None");
        cookie.setSecure(true); // HTTPS 설정 시 필요
        cookie.setPath("/"); // 쿠키의 경로 설정
        cookie.setHttpOnly(true); // HTTP 전용 설정 (클라이언트 스크립트에서 접근 불가)

        return cookie; // 생성된 쿠키 반환
    }
}