package org.pikalovanna.congratulationsbot.repository;

import org.pikalovanna.congratulationsbot.entity.Congratulation;
import org.pikalovanna.congratulationsbot.enums.ActionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface CongratulationRepository extends JpaRepository<Congratulation,Long> {
    List<Congratulation> findByUserUsernameOrderByIdAsc(String username);
    Page<Congratulation> findByUserUsernameOrderByIdAsc(String username, Pageable pageable);
    List<Congratulation> findByUserId(Long id);
    List<Congratulation> findByDateSendBeforeAndForwardIsNotNull(LocalDateTime dateTime);
    Congratulation findByStatusAndUserUsername(ActionStatus status, String username);

}
