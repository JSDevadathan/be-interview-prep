package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.entity.CustomerOrder;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByCustomerUsernameAndIdempotencyKey(String customerUsername, String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByIdAndCustomerUsername(Long id, String customerUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id and o.customerUsername = :customerUsername")
    Optional<CustomerOrder> findForUpdate(@Param("id") Long id, @Param("customerUsername") String customerUsername);
}
