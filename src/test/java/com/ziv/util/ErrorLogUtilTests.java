package com.ziv.util;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ErrorLogUtilTests {
    @Test
    void logsCauseLocationsAndSqlCodesWithoutRawCredentials() {
        Logger logger = mock(Logger.class);
        SQLException cause = new SQLException("password=private-password", "HY000", 1364);
        RuntimeException exception = new RuntimeException("code=012345 token=private-token", cause);
        ErrorLogUtil.log(logger, "Registration database operation failed", exception);
        ArgumentCaptor<Object> detail = ArgumentCaptor.forClass(Object.class);
        verify(logger).error(eq("{}"), detail.capture());
        assertThat(detail.getValue().toString())
                .contains("Registration database operation failed", "Caused by: java.sql.SQLException",
                        "SQLState=HY000", "errorCode=1364", "ErrorLogUtilTests.java:")
                .doesNotContain("private-password", "012345", "private-token");
    }
}
