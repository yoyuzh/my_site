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
}
