package ch.bzz.controller;

import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.model.Account;
import ch.bzz.model.Booking;
import ch.bzz.model.Project;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RestController
public class BookingApiController implements BookingApi {

    private static final int MAX_TEXT_LENGTH = 255;

    private final BookingRepository bookingRepository;
    private final AccountRepository accountRepository;
    private final CurrentProject currentProject;

    public BookingApiController(BookingRepository bookingRepository, AccountRepository accountRepository,
                                CurrentProject currentProject) {
        this.bookingRepository = bookingRepository;
        this.accountRepository = accountRepository;
        this.currentProject = currentProject;
    }

    @Override
    public ResponseEntity<List<ch.bzz.generated.model.Booking>> getBookings() {
        Project project = currentProject.get();
        List<ch.bzz.generated.model.Booking> result = bookingRepository.findByProject(project).stream()
                .sorted(Comparator.comparingInt(Booking::getBookingNumber))
                .map(BookingApiController::toApi)
                .toList();
        log.debug("Returning {} bookings for project '{}'", result.size(), project.getProjectName());
        return ResponseEntity.ok(result);
    }

    /**
     * Each entry is identified by its id (= booking number):
     * id missing or 0 -> new booking, all fields null -> delete, otherwise update the given fields.
     */
    @Override
    @Transactional
    public ResponseEntity<Void> updateBookings(UpdateBookingsRequest request) {
        Project project = currentProject.get();

        for (BookingUpdate update : request.getEntries()) {
            Integer id = update.getId();

            if (id == null || id == 0) {
                create(project, update);
                continue;
            }

            Booking booking = bookingRepository.findByProjectAndBookingNumber(project, id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Buchung " + id + " existiert nicht"));

            if (isDeletion(update)) {
                bookingRepository.delete(booking);
                log.info("Booking {} deleted in project '{}'", id, project.getProjectName());
                continue;
            }

            apply(project, booking, update);
            bookingRepository.save(booking);
            log.info("Booking {} updated in project '{}'", id, project.getProjectName());
        }
        return ResponseEntity.ok().build();
    }

    private void create(Project project, BookingUpdate update) {
        if (isDeletion(update)) {
            return; // a new row that was deleted again before saving
        }
        Booking booking = new Booking();
        booking.setProject(project);
        booking.setBookingNumber(bookingRepository.findMaxBookingNumber(project) + 1);
        apply(project, booking, update);

        if (booking.getDate() == null || booking.getText() == null || booking.getDebitAccount() == null
                || booking.getCreditAccount() == null || booking.getAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neue Buchung ist unvollständig");
        }
        bookingRepository.save(booking);
        log.info("Booking {} created in project '{}'", booking.getBookingNumber(), project.getProjectName());
    }

    /** Copies all fields that are present in the request. A present field must not be null. */
    private void apply(Project project, Booking booking, BookingUpdate update) {
        if (isSet(update.getDate())) {
            booking.setDate(required(update.getDate(), "Datum"));
        }
        if (isSet(update.getText())) {
            String text = required(update.getText(), "Text").trim();
            if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Buchungstext");
            }
            booking.setText(text);
        }
        if (isSet(update.getDebit())) {
            booking.setDebitAccount(findAccount(project, required(update.getDebit(), "Soll")));
        }
        if (isSet(update.getCredit())) {
            booking.setCreditAccount(findAccount(project, required(update.getCredit(), "Haben")));
        }
        if (isSet(update.getAmount())) {
            BigDecimal amount = BigDecimal.valueOf(required(update.getAmount(), "Betrag"))
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Betrag muss positiv sein");
            }
            booking.setAmount(amount);
        }
    }

    private Account findAccount(Project project, int number) {
        // Only accounts of the own project can be used.
        return accountRepository.findByProjectAndAccountNumber(project, number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Konto " + number + " existiert nicht"));
    }

    private static boolean isDeletion(BookingUpdate update) {
        return Stream.of(update.getDate(), update.getText(), update.getDebit(), update.getCredit(), update.getAmount())
                .allMatch(field -> field == null || !field.isPresent() || field.get() == null);
    }

    private static boolean isSet(JsonNullable<?> field) {
        return field != null && field.isPresent();
    }

    private static <T> T required(JsonNullable<T> field, String label) {
        T value = field.get();
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " darf nicht leer sein");
        }
        return value;
    }

    private static ch.bzz.generated.model.Booking toApi(Booking booking) {
        ch.bzz.generated.model.Booking api = new ch.bzz.generated.model.Booking();
        api.setNumber(booking.getBookingNumber());
        api.setDate(booking.getDate());
        api.setText(booking.getText());
        api.setDebit(booking.getDebitAccount().getAccountNumber());
        api.setCredit(booking.getCreditAccount().getAccountNumber());
        api.setAmount(booking.getAmount().floatValue());
        return api;
    }
}
