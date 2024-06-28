package org.example.shallweeatbackend.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.shallweeatbackend.dto.CustomOAuth2User;
import org.example.shallweeatbackend.jwt.JWTUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * CustomSuccessHandler 클래스는 인증 성공 시 호출되는 핸들러입니다.
 * 인증 성공 후 JWT 토큰을 생성하고, 이를 쿠키에 저장하여 응답에 추가합니다.
 */
@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTUtil jwtUtil; // JWT 토큰 생성을 위한 유틸리티 클래스

    // 인증 성공 시 호출
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        // 인증된 사용자 정보 가져오기
        CustomOAuth2User customUserDetails = (CustomOAuth2User) authentication.getPrincipal();

        String providerId = customUserDetails.getProviderId(); // 사용자의 providerId 가져오기

        // 사용자의 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

        // JWT 토큰 생성
        String access = jwtUtil.createJwt("access", providerId, role, 600000L);
        String refresh = jwtUtil.createJwt("refresh", providerId, role, 86400000L);

        // 응답 설정
        response.setHeader("access", access);
        response.addCookie(createCookie("refresh", refresh));
        response.setStatus(HttpStatus.OK.value());
        // TODO: 프론트엔드와 논의하여 적절한 리다이렉트 URL로 수정 필요
        response.sendRedirect("http://localhost:8080/");
    }

    // 쿠키 생성
    private Cookie createCookie(String key, String value) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24*60*60); // 쿠키의 유효 기간 설정 (초 단위)
        //cookie.setSecure(true); // HTTPS 설정 시 필요
        cookie.setPath("/"); // 쿠키의 경로 설정
        cookie.setHttpOnly(true); // HTTP 전용 설정 (클라이언트 스크립트에서 접근 불가)

        return cookie; // 생성된 쿠키 반환
    }
}
