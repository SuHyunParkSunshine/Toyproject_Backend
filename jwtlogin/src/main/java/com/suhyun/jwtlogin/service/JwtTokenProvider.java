// Spring Security와 JWT 토큰을 사용하여 인증과 권한 부여를 처리하는 클래스
// 이 클래스에서 JWT 토큰의 생성, 복호화, 검증 기능 구현
package com.suhyun.jwtlogin.service;

import org.springframework.stereotype.Component;

import com.suhyun.jwtlogin.dto.JwtTokenDto;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Key key;

    // application.properties에서 secret값 가져와서 key에 저장
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes); // JWT 토큰의 생성, 검증 등에 사용될 서명 키
    }

    // Member 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드
    public JwtTokenDto generateToken(UserDetails userDetails) { //Authentication 객체 : 현재 인증된 사용자의 정보를 나타내는 객체
        //권한 가져오기
        String authorities = userDetails.getAuthorities().stream()
                                        .map(GrantedAuthority::getAuthority)
                                        .collect(Collectors.joining(","));

        long now = (new Date()).getTime(); //현재 시간을 밀리초 단위로 얻는 방법

        // Access Token 생성: 인증된 사용자의 권한 정보와 만료시간을 담고 있음
        Date accessTokenExpiresIn = new Date(now + 86400000);
        String accessToken = Jwts.builder()
                                .setSubject(userDetails.getUsername())
                                .claim("auth", authorities)
                                .setExpiration(accessTokenExpiresIn)
                                .signWith(key, SignatureAlgorithm.HS256)
                                .compact();

        // Refresh Token 생성
        String refreshToken = Jwts.builder()
                                .setExpiration(new Date(now + 86400000))
                                .signWith(key, SignatureAlgorithm.HS256)
                                .compact();
        
        return JwtTokenDto.builder()
                .grantType("Bearer ")
                .accessToken("Bearer " + accessToken)
                .refreshToken("Bearer " + refreshToken)
                .build();
    }

    // Jwt 토큰을 복호화하여 토큰에 들어있는 정보를 꺼내는 메서드
    public Authentication getAuthentication(String accessToken) {
        // Jwt 토큰 복호화
        Claims claims = parseClaims(accessToken);

        if(claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        // 클레임에서 권한 정보 가져오기
        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("auth").toString().split(","))
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        // UserDetails 객체를 만들어서 Authentication return
        // UserDetails: interface, User: UserDetails를 구현한 class
        UserDetails principal = new User(claims.getSubject(),"",authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    // 토큰 정보를 검증하는 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch(SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch(ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
        } catch(UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch(IllegalArgumentException e) {
            log.info("JWT claims string is empty", e);
        }
        return false;
    }

    // accessToken
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(accessToken)
                        .getBody();

        } catch(ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
