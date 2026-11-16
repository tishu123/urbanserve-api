package com.example.urbanserve.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.urbanserve.model.Booking;
import com.example.urbanserve.model.BookingStatus;
import com.example.urbanserve.payment.PaymentClient;
import com.example.urbanserve.repo.BookingRepository;

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
        Booking booking = bookings.findById(bookingId)
                .filter(found -> found.getCustomerPhone().equals(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() != BookingStatus.PENDING
                && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cash payment can only be selected for a pending or confirmed booking");
        }

        Long amountPaise = booking.getTotalAmountPaise();
        if (amountPaise == null || amountPaise <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This booking does not have a saved package price");
        }

        return paymentClient.registerCashPayment(booking.getId(), amountPaise);
    }
}