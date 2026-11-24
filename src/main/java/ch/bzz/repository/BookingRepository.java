package ch.bzz.repository;

import ch.bzz.model.Account;
import ch.bzz.model.Booking;
import ch.bzz.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByProject(Project project);

    Optional<Booking> findByProjectAndBookingNumber(Project project, int bookingNumber);

    @Query("select coalesce(max(b.bookingNumber), 0) from Booking b where b.project = :project")
    int findMaxBookingNumber(@Param("project") Project project);

    boolean existsByDebitAccountOrCreditAccount(Account debitAccount, Account creditAccount);
}
