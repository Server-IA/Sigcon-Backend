package com.sigcon.backend.security;

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

        extraClaims.put("authorities", authorities);

        return generateToken(extraClaims, userDetails);
    }


    public String generateToken(Map<String,Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()

                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 30)) // 30 minutes
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
