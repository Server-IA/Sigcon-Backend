package com.sigcon.backend.general.security;

import com.sigcon.backend.parametrization.users.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    private Key getSignInKey(){
        byte[] KeyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(KeyBytes);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();

        List<String> authorities = userDetails.getAuthorities()
                .stream()
                .map(auth -> auth.getAuthority())
                .collect(Collectors.toList());

        // Claims multi-tenant (V10-A, 2026-04-19): si el UserDetails es nuestra
        // entidad User, agregamos al JWT el companyId y el platformRole para que
        // TenantContextFilter los pueda leer en cada request.
        // Invariante garantizado por ck_users_tenant_or_platform: o platformRole
        // es PLATFORM_ADMIN y companyId es null, o platformRole es null y
        // companyId no es null. Nunca ambos, nunca ninguno.
        if (userDetails instanceof User u) {
            extraClaims.put("userId", u.getId());
            if (u.getPlatformRole() != null) {
                extraClaims.put("platformRole", u.getPlatformRole());
                // Inyectar authority PLATFORM_ADMIN para que @PreAuthorize lo
                // reconozca en los endpoints /api/platform/**. Se distingue de
                // ROLE_ADMIN (admin de empresa) — un admin de empresa tiene
                // ROLE_ADMIN pero NO PLATFORM_ADMIN.
                if (!authorities.contains(u.getPlatformRole())) {
                    authorities = new java.util.ArrayList<>(authorities);
                    authorities.add(u.getPlatformRole());
                }
            }
            if (u.getCompanyId() != null) {
                extraClaims.put("companyId", u.getCompanyId());
            }
        }

        extraClaims.put("authorities", authorities);

        return generateToken(extraClaims, userDetails);
    }


    public String generateToken(Map<String,Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()

                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 240)) // 30 minutes
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Claims getAllClaims(String token){
        try {
            return Jwts
                    .parser()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new RuntimeException("Invalid or expired JWT token", e);
        }
    }


    private <T> T getClaim(String token, Function<Claims,T> claimsT){
        Claims claims = getAllClaims(token);
        return claimsT.apply(claims);

    }

    public String getUsername(String token){
        return getClaim(token,Claims::getSubject);
    }

    private Date getExpirationDate (String token){
        return getClaim(token,Claims::getExpiration);
    }

    private boolean tokenExpired (String token){
        return  getClaim(token,Claims::getExpiration).before(new Date());
    }

    public  boolean validateToken (String token, UserDetails userDetails){
        final String username = getUsername(token);

        return (username.equals(userDetails.getUsername()) && !tokenExpired(token));
    }
}
