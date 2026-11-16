package com.example.urbanserve.web;

import com.example.urbanserve.model.Booking;
import com.example.urbanserve.model.BookingStatus;
import com.example.urbanserve.payment.PaymentClient;
import com.example.urbanserve.repo.BookingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;



@RestController
@RequestMapping("/api/bookings")
public class BookingPaymentController {

    private final BookingRepository bookings;
    private final PaymentClient paymentClient;

    public BookingPaymentController(BookingRepository bookings, PaymentClient paymentClient) {
        this.bookings = bookings;
        this.paymentClient = paymentClient;
    }

    @PostMapping("/{bookingId}/payment/cash")
    public PaymentClient.PaymentDetails registerCashPayment(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        Booking booking = requireOwnedBooking(bookingId, jwt);

        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cash payment can only be selected for a pending or confirmed booking");
        }

        return paymentClient.registerCashPayment(booking.getId(), requireSavedTotal(booking));
    }

    @PostMapping("/{bookingId}/payment/online")
    public PaymentClient.OnlinePaymentDetails prepareOnlinePayment(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        Booking booking = requireOwnedBooking(bookingId, jwt);

        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Online payment can only be started for a pending or confirmed booking");
        }

        long amountPaise = requireSavedTotal(booking);
        if (amountPaise < 100) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Online payment must be at least 100 paise");
        }

        return paymentClient.prepareOnlinePayment(booking.getId(), amountPaise);
    }

    @PostMapping("/{bookingId}/payment/online/verify")
    public PaymentClient.VerifiedOnlinePaymentDetails verifyOnlinePayment(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyOnlinePaymentRequest request) {
        Booking booking = requireOwnedBooking(bookingId, jwt);

        // A booking may be cancelled after checkout starts. Verification records
        // what happened to the payment without reopening or dispatching the booking.
        return paymentClient.verifyOnlinePayment(
                booking.getId(),
                requireSavedTotal(booking),
                request.razorpayPaymentId(),
                request.razorpaySignature());
    }
    @GetMapping("/{bookingId}/payment/status")
    public ResponseEntity<PaymentClient.SavedPaymentStatus> getPaymentStatus(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal Jwt jwt) {
        Booking booking = requireOwnedBooking(bookingId, jwt);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(paymentClient.getPaymentStatus(
                        booking.getId(), booking.getTotalAmountPaise()));
    }

    private Booking requireOwnedBooking(Long bookingId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
        }

        return bookings.findById(bookingId)
                .filter(booking -> jwt.getSubject().equals(booking.getCustomerPhone()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private long requireSavedTotal(Booking booking) {
        Long amountPaise = booking.getTotalAmountPaise();
        if (amountPaise == null || amountPaise <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking does not have a saved package price");
        }
        return amountPaise;
    }

    public record VerifyOnlinePaymentRequest(
            @NotBlank @Pattern(regexp = "pay_[A-Za-z0-9]{1,100}") String razorpayPaymentId,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String razorpaySignature) {}
}