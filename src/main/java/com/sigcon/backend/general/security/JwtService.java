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
        return generateToken(userDetails, java.util.Collections.emptyMap());
    }

    /**
     * PA-RF-01 v3.0 (Control de Cambios PA, 2026-05-29): genera el access token
     * incluyendo claims adicionales (ej. {@code sessionId} para correlacionar el
     * token con la sesion activa y permitir logout/revocacion por sesion).
     */
    public String generateToken(UserDetails userDetails, Map<String, Object> additionalClaims) {
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

        if (additionalClaims != null && !additionalClaims.isEmpty()) {
            extraClaims.putAll(additionalClaims);
        }

        return generateToken(extraClaims, userDetails);
    }


    public String generateToken(Map<String,Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()

                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                // PA-RF-27 (Pendientes PA): identificador unico del token (claim jti).
                // setId va DESPUES de setClaims (que reemplaza el mapa completo) para
                // que no se sobreescriba. El logout almacena este jti + la expiracion
                // en blacklisted_tokens, lo que permite al job de limpieza purgar
                // entradas ya vencidas en lugar de acumularlas indefinidamente.
                .setId(java.util.UUID.randomUUID().toString())
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

    /**
     * PA-RF-01 v3.0: lee el claim {@code sessionId} del token (o null si no
     * existe / el token es invalido). Usado por logout para revocar la sesion
     * exacta del usuario.
     */
    public String getSessionId(String token){
        try {
            Object v = getAllClaims(token).get("sessionId");
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Date getExpirationDate (String token){
        return getClaim(token,Claims::getExpiration);
    }

    /**
     * PA-RF-27 (Pendientes PA): lee el claim {@code jti} del token (o null si no
     * existe / el token es invalido). Usado por logout para guardar el id del
     * token en la blacklist y por el job de limpieza para identificarlo.
     */
    public String getJti(String token){
        try {
            return getClaim(token, Claims::getId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * PA-RF-27 (Pendientes PA): fecha de expiracion del token (o null si el
     * token es invalido). La blacklist guarda este valor para que el scheduler
     * purgue las entradas vencidas.
     */
    public Date getExpiration(String token){
        try {
            return getExpirationDate(token);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean tokenExpired (String token){
        return  getClaim(token,Claims::getExpiration).before(new Date());
    }

    public  boolean validateToken (String token, UserDetails userDetails){
        final String username = getUsername(token);
        if (!username.equals(userDetails.getUsername()) || tokenExpired(token)) {
            return false;
        }

        // QA Bloque AV (HU-PA-11 E4 + HU-PA-12 E4, 2026-05-14): NO invalidamos
        // el token por sessionInvalidatedAt. Antes (Bug 79) cualquier cambio
        // de rol invalidaba el token aqui forzando re-login. La HU dice que
        // el usuario debe PERMANECER en sesion y solo recomputar permisos en
        // la siguiente request. Eso lo hace EffectivePermissionsFilter en cada
        // request HTTP autenticado normal.
        //
        // Este metodo se sigue usando para validar tokens en flujos no-HTTP
        // (ej. SSE/WebSocket en SseTokenAuthFilter). Para esos casos los
        // permisos no son criticos al validar la conexion inicial - la
        // re-evaluacion ocurre cuando el cliente hace requests.
        return true;
    }
}
