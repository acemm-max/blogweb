package com.ziv.user.infrastructure;

import com.ziv.user.dto.EmailCodePurpose;

public interface EmailCodeStore {
    boolean reserve(String email, String code, String reservationId);

    void invalidate(String email, String reservationId);

    boolean consume(String email, String code);

    boolean reserve(String email, String code, String reservationId, EmailCodePurpose purpose);

    void invalidate(String email, String reservationId, EmailCodePurpose purpose);

    boolean consume(String email, String code, EmailCodePurpose purpose);

    boolean isCurrent(String email, String reservationId, EmailCodePurpose purpose);
}
