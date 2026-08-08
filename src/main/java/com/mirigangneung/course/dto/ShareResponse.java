package com.mirigangneung.course.dto; import java.time.*; public record ShareResponse(String shareToken,String shareUrl,OffsetDateTime expiresAt){}
