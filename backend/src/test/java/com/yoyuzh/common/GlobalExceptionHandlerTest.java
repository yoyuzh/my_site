package com.yoyuzh.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturn400ForUnknownBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.UNKNOWN, "操作失败");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().msg()).isEqualTo("操作失败");
    }

    @Test
    void shouldReturn401ForNotLoggedInException() {
        BusinessException ex = new BusinessException(ErrorCode.NOT_LOGGED_IN, "未登录");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn403ForPermissionDeniedException() {
        BusinessException ex = new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn404ForFileNotFoundException() {
        BusinessException ex = new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn400WithFirstValidationMessageForMethodArgumentNotValidException() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "username", "用户名不能为空"));
        bindingResult.addError(new FieldError("target", "email", "邮箱格式不正确"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().msg()).isEqualTo("用户名不能为空");
    }

    @Test
    void shouldReturn400WithDefaultMessageWhenNoValidationMessages() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().msg()).isEqualTo("请求参数不合法");
    }

    @Test
    void shouldReturn400ForConstraintViolationException() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("密码不能为空");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().msg()).isEqualTo("密码不能为空");
    }

    @Test
    void shouldReturn403ForAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("forbidden");
        ResponseEntity<ApiResponse<Void>> response = handler.handleAccessDenied(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().msg()).isEqualTo("没有权限访问该资源");
    }

    @Test
    void shouldReturn401ForBadCredentialsException() {
        BadCredentialsException ex = new BadCredentialsException("bad credentials");
        ResponseEntity<ApiResponse<Void>> response = handler.handleBadCredentials(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().msg()).isEqualTo("用户名或密码错误");
    }

    @Test
    void shouldReturn500ForUnhandledException() {
        RuntimeException ex = new RuntimeException("unexpected");
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknown(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().msg()).isEqualTo("服务器内部错误");
    }
}
