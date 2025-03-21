package dev.danvega;

import java.time.LocalDateTime;

public record BlogPost(
        Long id,
        String title,
        String author,
        String content,
        String category,
        LocalDateTime publishedDate
) {}
