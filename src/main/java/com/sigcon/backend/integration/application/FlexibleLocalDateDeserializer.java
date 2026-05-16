package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * QA Bloque AX (HU-INT-13 tolerancia formatos, 2026-05-16): deserializa fechas
 * de AgroFusion en multiples formatos hacia {@link LocalDate}.
 *
 * <p>AgroFusion envia las fechas en distintos formatos segun el sistema origen:
 * <ul>
 *   <li>{@code 2026-03-07} - ISO LocalDate (estandar AAEF v1.0)</li>
 *   <li>{@code 10-02-2026 07:34:33 AM} - dd-MM-yyyy con hora 12h (Sigma)</li>
 *   <li>{@code 10-02-2026 07:34:34} - dd-MM-yyyy con hora 24h</li>
 *   <li>{@code 2025-11-06 15:07:17.010993+00:00} - Postgres timestamptz</li>
 *   <li>{@code 2026-03-07T15:07:17Z} - ISO_OFFSET_DATE_TIME</li>
 * </ul>
 *
 * <p>El deserializer intenta cada formato en orden. Cuando matchea, descarta
 * la hora (si la tiene) y retorna solo la parte de fecha como {@link LocalDate}.
 * Si ninguno aplica, lanza IOException con mensaje claro para que el lote sea
 * marcado FAILED con {@code MAPPING_ERROR} con detalle del string ofensor.
 *
 * <p>Aplicado via {@code @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)}
 * en los campos LocalDate de los DTOs AAEF.
 */
public class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final List<DateTimeFormatter> DATE_ONLY_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE,                    // 2026-03-07
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
    );

    private static final List<DateTimeFormatter> DATETIME_FORMATTERS = Arrays.asList(
            // Sigma 12h AM/PM
            DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("dd-MM-yyyy h:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.US),
            // 24h
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.US),
            // Postgres timestamp sin offset
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US),
            // ISO_LOCAL_DATE_TIME
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    private static final List<DateTimeFormatter> OFFSET_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,              // 2025-11-06T15:07:17+00:00
            // Postgres timestamptz: "2025-11-06 15:07:17.010993+00:00"
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSxxx", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSX", Locale.US)
    );

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        String raw = p.getText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();

        // 1. Intentar parsers de fecha pura (sin hora) - lo mas eficiente y comun
        for (DateTimeFormatter fmt : DATE_ONLY_FORMATTERS) {
            try {
                return LocalDate.parse(text, fmt);
            } catch (Exception ignore) {
                // continue
            }
        }

        // 2. Intentar parsers con offset (timestamptz)
        for (DateTimeFormatter fmt : OFFSET_FORMATTERS) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(text, fmt);
                return odt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            } catch (Exception ignore) {
                // continue
            }
        }

        // 3. Intentar parsers de timestamp sin offset (asume local)
        for (DateTimeFormatter fmt : DATETIME_FORMATTERS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(text, fmt);
                return ldt.toLocalDate();
            } catch (Exception ignore) {
                // continue
            }
        }

        // 4. Ningun formato aplico
        throw new IOException("Formato de fecha no soportado: '" + text
                + "'. Formatos aceptados: yyyy-MM-dd (ISO), dd-MM-yyyy [hh:mm:ss a],"
                + " dd/MM/yyyy [HH:mm:ss], yyyy-MM-dd HH:mm:ss[.SSSSSS][+ZZ:ZZ].");
    }
}
