package ch.bzz.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A booking (Buchung) with debit and credit account. The booking number is
 * unique per project and is what the API exposes as "number".
 */
@Entity
@Table(name = "booking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_name", "booking_number"}))
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_number", nullable = false)
    private int bookingNumber;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "text", nullable = false, length = 255)
    private String text;

    @ManyToOne(optional = false)
    @JoinColumn(name = "debit_account_id", nullable = false)
    private Account debitAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "credit_account_id", nullable = false)
    private Account creditAccount;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_name", nullable = false)
    private Project project;
}
