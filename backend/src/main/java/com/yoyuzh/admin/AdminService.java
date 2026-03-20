package com.yoyuzh.admin;

import com.yoyuzh.auth.PasswordPolicy;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRole;
import com.yoyuzh.auth.UserRepository;
import com.yoyuzh.auth.RefreshTokenService;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import com.yoyuzh.common.PageResponse;
import com.yoyuzh.cqu.CourseRepository;
import com.yoyuzh.cqu.GradeRepository;
import com.yoyuzh.files.FileService;
import com.yoyuzh.files.StoredFile;
import com.yoyuzh.files.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;
    private final CourseRepository courseRepository;
    private final GradeRepository gradeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSummaryResponse getSummary() {
        return new AdminSummaryResponse(
                userRepository.count(),
                storedFileRepository.count(),
                userRepository.countByLastSchoolStudentIdIsNotNull()
        );
    }

    public PageResponse<AdminUserResponse> listUsers(int page, int size, String query) {
        Page<User> result = userRepository.searchByUsernameOrEmail(
                normalizeQuery(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminUserResponse> items = result.getContent().stream()
                .map(this::toUserResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminFileResponse> listFiles(int page, int size, String query, String ownerQuery) {
        Page<StoredFile> result = storedFileRepository.searchAdminFiles(
                normalizeQuery(query),
                normalizeQuery(ownerQuery),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "user.username")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")))
        );
        List<AdminFileResponse> items = result.getContent().stream()
                .map(this::toFileResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    public PageResponse<AdminSchoolSnapshotResponse> listSchoolSnapshots(int page, int size) {
        Page<User> result = userRepository.findByLastSchoolStudentIdIsNotNull(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AdminSchoolSnapshotResponse> items = result.getContent().stream()
                .map(this::toSchoolSnapshotResponse)
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), page, size);
    }

    @Transactional
    public void deleteFile(Long fileId) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "文件不存在"));
        fileService.delete(storedFile.getUser(), fileId);
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, UserRole role) {
        User user = getRequiredUser(userId);
        user.setRole(role);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserBanned(Long userId, boolean banned) {
        User user = getRequiredUser(userId);
        user.setBanned(banned);
        refreshTokenService.revokeAllForUser(user.getId());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUserPassword(Long userId, String newPassword) {
        if (!PasswordPolicy.isStrong(newPassword)) {
            throw new BusinessException(ErrorCode.UNKNOWN, "密码至少10位，且必须包含大写字母、小写字母、数字和特殊字符");
        }
        User user = getRequiredUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllForUser(user.getId());
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminPasswordResetResponse resetUserPassword(Long userId) {
        String temporaryPassword = generateTemporaryPassword();
        updateUserPassword(userId, temporaryPassword);
        return new AdminPasswordResetResponse(temporaryPassword);
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getLastSchoolStudentId(),
                user.getLastSchoolSemester(),
                user.getRole(),
                user.isBanned()
        );
    }

    private AdminFileResponse toFileResponse(StoredFile storedFile) {
        User owner = storedFile.getUser();
        return new AdminFileResponse(
                storedFile.getId(),
                storedFile.getFilename(),
                storedFile.getPath(),
                storedFile.getSize(),
                storedFile.getContentType(),
                storedFile.isDirectory(),
                storedFile.getCreatedAt(),
                owner.getId(),
                owner.getUsername(),
                owner.getEmail()
        );
    }

    private AdminSchoolSnapshotResponse toSchoolSnapshotResponse(User user) {
        String studentId = user.getLastSchoolStudentId();
        String semester = user.getLastSchoolSemester();
        long scheduleCount = studentId == null || semester == null
                ? 0
                : courseRepository.countByUserIdAndStudentIdAndSemester(user.getId(), studentId, semester);
        long gradeCount = studentId == null || semester == null
                ? 0
                : gradeRepository.countByUserIdAndStudentIdAndSemester(user.getId(), studentId, semester);

        return new AdminSchoolSnapshotResponse(
                user.getId(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                studentId,
                semester,
                scheduleCount,
                gradeCount
        );
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNKNOWN, "用户不存在"));
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private String generateTemporaryPassword() {
        String lowers = "abcdefghjkmnpqrstuvwxyz";
        String uppers = "ABCDEFGHJKMNPQRSTUVWXYZ";
        String digits = "23456789";
        String specials = "!@#$%^&*";
        String all = lowers + uppers + digits + specials;
        char[] password = new char[12];
        password[0] = lowers.charAt(secureRandom.nextInt(lowers.length()));
        password[1] = uppers.charAt(secureRandom.nextInt(uppers.length()));
        password[2] = digits.charAt(secureRandom.nextInt(digits.length()));
        password[3] = specials.charAt(secureRandom.nextInt(specials.length()));
        for (int i = 4; i < password.length; i += 1) {
            password[i] = all.charAt(secureRandom.nextInt(all.length()));
        }
        for (int i = password.length - 1; i > 0; i -= 1) {
            int j = secureRandom.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }
}
