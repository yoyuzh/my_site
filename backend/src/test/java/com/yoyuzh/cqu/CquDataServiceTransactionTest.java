package com.yoyuzh.cqu;

import com.yoyuzh.PortalBackendApplication;
import com.yoyuzh.auth.User;
import com.yoyuzh.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = PortalBackendApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:cqu_tx_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.cqu.require-login=true",
                "app.cqu.mock-enabled=false"
        }
)
class CquDataServiceTransactionTest {

    @Autowired
    private CquDataService cquDataService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @MockBean
    private CquApiClient cquApiClient;

    @Test
    void shouldPersistGradesInsideTransactionForLoggedInUser() {
        User user = new User();
        user.setUsername("portal-demo");
        user.setEmail("portal-demo@example.com");
        user.setPasswordHash("encoded");
        user = userRepository.save(user);

        Grade existing = new Grade();
        existing.setUser(user);
        existing.setCourseName("Old Java");
        existing.setGrade(60D);
        existing.setSemester("2025-spring");
        existing.setStudentId("2023123456");
        gradeRepository.save(existing);

        when(cquApiClient.fetchGrades("2025-spring", "2023123456")).thenReturn(List.of(
                Map.of(
                        "courseName", "Java",
                        "grade", 95,
                        "semester", "2025-spring"
                )
        ));

        List<GradeResponse> response = cquDataService.getGrades(user, "2025-spring", "2023123456");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).courseName()).isEqualTo("Java");
        assertThat(response.get(0).grade()).isEqualTo(95D);
        assertThat(gradeRepository.findByUserIdAndStudentIdOrderBySemesterAscGradeDesc(user.getId(), "2023123456"))
                .hasSize(1)
                .first()
                .extracting(Grade::getCourseName)
                .isEqualTo("Java");
    }
}
