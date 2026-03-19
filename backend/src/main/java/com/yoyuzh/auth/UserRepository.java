package com.yoyuzh.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    long countByLastSchoolStudentIdIsNotNull();

    Page<User> findByLastSchoolStudentIdIsNotNull(Pageable pageable);

    @Query("""
            select u from User u
            where (:query is null or :query = ''
                or lower(u.username) like lower(concat('%', :query, '%'))
                or lower(u.email) like lower(concat('%', :query, '%')))
            """)
    Page<User> searchByUsernameOrEmail(@Param("query") String query, Pageable pageable);
}
