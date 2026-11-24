package ch.bzz.repository;

import ch.bzz.model.Account;
import ch.bzz.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByProject(Project project);

    Optional<Account> findByProjectAndAccountNumber(Project project, int accountNumber);
}
