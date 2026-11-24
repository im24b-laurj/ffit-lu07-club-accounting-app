package ch.bzz.controller;

import ch.bzz.generated.api.AccountApi;
import ch.bzz.generated.model.AccountUpdate;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.model.Account;
import ch.bzz.model.Project;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
public class AccountApiController implements AccountApi {

    private static final int MAX_NAME_LENGTH = 100;

    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;
    private final CurrentProject currentProject;

    public AccountApiController(AccountRepository accountRepository, BookingRepository bookingRepository,
                                CurrentProject currentProject) {
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
        this.currentProject = currentProject;
    }

    @Override
    public ResponseEntity<List<ch.bzz.generated.model.Account>> getAccounts() {
        Project project = currentProject.get();
        List<ch.bzz.generated.model.Account> result = accountRepository.findByProject(project).stream()
                .sorted(Comparator.comparingInt(Account::getAccountNumber))
                .map(AccountApiController::toApi)
                .toList();
        log.debug("Returning {} accounts for project '{}'", result.size(), project.getProjectName());
        return ResponseEntity.ok(result);
    }

    /**
     * Each entry is identified by its number:
     * name = null -> delete the account, name set -> create or rename, name missing -> nothing.
     */
    @Override
    @Transactional
    public ResponseEntity<Void> updateAccounts(UpdateAccountsRequest request) {
        Project project = currentProject.get();

        for (AccountUpdate update : request.getAccounts()) {
            Integer number = update.getNumber();
            if (number == null || number <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Kontonummer");
            }
            if (update.getName() == null || !update.getName().isPresent()) {
                continue;
            }

            Optional<Account> existing = accountRepository.findByProjectAndAccountNumber(project, number);
            String name = update.getName().get();

            if (name == null) {
                existing.ifPresent(account -> delete(project, account));
                continue;
            }

            name = name.trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Kontoname");
            }
            Account account = existing.orElseGet(() -> {
                Account created = new Account();
                created.setProject(project);
                created.setAccountNumber(number);
                return created;
            });
            account.setName(name);
            accountRepository.save(account);
            log.info("Account {} saved in project '{}'", number, project.getProjectName());
        }
        return ResponseEntity.ok().build();
    }

    private void delete(Project project, Account account) {
        if (bookingRepository.existsByDebitAccountOrCreditAccount(account, account)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Konto " + account.getAccountNumber() + " wird noch in Buchungen verwendet");
        }
        accountRepository.delete(account);
        log.info("Account {} deleted in project '{}'", account.getAccountNumber(), project.getProjectName());
    }

    private static ch.bzz.generated.model.Account toApi(Account account) {
        ch.bzz.generated.model.Account api = new ch.bzz.generated.model.Account();
        api.setNumber(account.getAccountNumber());
        api.setName(account.getName());
        return api;
    }
}
