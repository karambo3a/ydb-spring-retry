package tech.ydb.retry.integration.app;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import org.springframework.data.repository.query.Param;

public interface SimpleUserRepository extends ListCrudRepository<User, Long> {

    List<User> findByLastname(String lastname);

    @Modifying
    @Query("UPDATE Users SET firstname = :newFirstname WHERE id = :id")
    void updateFirstnameById(@Param("id") Long id, @Param("newFirstname") String newFirstname);
}
