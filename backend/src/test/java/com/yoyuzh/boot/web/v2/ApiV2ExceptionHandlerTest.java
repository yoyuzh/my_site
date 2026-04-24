package com.yoyuzh.boot.web.v2;

import com.yoyuzh.shared.kernel.BusinessException;
import com.yoyuzh.shared.kernel.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiV2ExceptionHandlerTest {

    private final ApiV2ExceptionHandler handler = new ApiV2ExceptionHandler();

    @Test
    void shouldMapV2BusinessErrorsToV2ResponseEnvelope() {
        ResponseEntity<ApiV2Response<Void>> response = handler.handleApiV2Exception(
                new ApiV2Exception(ApiV2ErrorCode.FILE_NOT_FOUND, "文件不存在")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiV2ErrorCode.FILE_NOT_FOUND.getCode());
        assertThat(response.getBody().msg()).isEqualTo("文件不存在");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void shouldKeepUnknownV2ErrorsInsideTheV2ErrorCodeRange() {
        ResponseEntity<ApiV2Response<Void>> response = handler.handleUnknownException(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiV2ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getBody().msg()).isEqualTo("服务器内部错误");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void shouldMapLegacyBusinessExceptionToV2Envelope() {
        ResponseEntity<ApiV2Response<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.UNKNOWN, "duplicate target")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiV2ErrorCode.BAD_REQUEST.getCode());
        assertThat(response.getBody().msg()).isEqualTo("duplicate target");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void shouldMapExpiredSessionBusinessExceptionToDistinctV2Envelope() {
        ResponseEntity<ApiV2Response<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.SESSION_EXPIRED, "share expired")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiV2ErrorCode.SESSION_EXPIRED.getCode());
        assertThat(response.getBody().msg()).isEqualTo("share expired");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void shouldMapSemanticBusinessExceptionsToDistinctV2Envelope() {
        assertBusinessMapping(ErrorCode.INVALID_INPUT, HttpStatus.BAD_REQUEST, ApiV2ErrorCode.INVALID_INPUT);
        assertBusinessMapping(ErrorCode.DUPLICATE_NAME, HttpStatus.CONFLICT, ApiV2ErrorCode.DUPLICATE_NAME);
        assertBusinessMapping(ErrorCode.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, ApiV2ErrorCode.QUOTA_EXCEEDED);
    }

    private void assertBusinessMapping(ErrorCode sourceCode,
                                       HttpStatus expectedStatus,
                                       ApiV2ErrorCode expectedCode) {
        ResponseEntity<ApiV2Response<Void>> response = handler.handleBusinessException(
                new BusinessException(sourceCode, "semantic error")
        );

        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(expectedCode.getCode());
        assertThat(response.getBody().msg()).isEqualTo("semantic error");
        assertThat(response.getBody().data()).isNull();
    }
}
