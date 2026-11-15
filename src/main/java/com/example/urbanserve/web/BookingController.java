package com.example.urbanserve.web;

import com.example.urbanserve.dispatch.DispatchOutbox;
import com.example.urbanserve.model.*;
import com.example.urbanserve.repo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All endpoints require a JWT from /api/auth/otp/verify.
 * Customers only see and cancel their own bookings (phone = token subject).
 * ADMIN can view any booking, look up by phone and set any status.
 *
 * A new booking is PENDING until urbanserve-dispatch picks a free professional (see
 * AssignmentHandler). The booking and the message to dispatch are saved in one transaction.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    public record BookingRequest(
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Size(max = 500) String address,
            @NotNull @Future LocalDateTime scheduledAt,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 80) String city,
            Long providerId) {}

    private final BookingRepository bookings;
    private final CategoryRepository categories;
    private final ProviderRepository providers;
    private final DispatchOutbox dispatch;

    public BookingController(BookingRepository bookings, CategoryRepository categories, ProviderRepository providers,
                             DispatchOutbox dispatch) {
        this.bookings = bookings;
        this.categories = categories;
        this.providers = providers;
        this.dispatch = dispatch;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Booking create(@Valid @RequestBody BookingRequest r, @AuthenticationPrincipal Jwt jwt) {
        ServiceCategory category = categories.findById(r.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        // Optional preferred professional; dispatch tries them first if they're free.
        Provider preferred = null;
        if (r.providerId() != null) {
            preferred = providers.findById(r.providerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
            if (!preferred.getCategory().getId().equals(category.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider does not offer this service");
            }
        }

        Booking b = new Booking();
        b.setCustomerName(r.customerName().trim());
        b.setCustomerPhone(jwt.getSubject()); // verified phone from the token, never from the request body
        b.setAddress(r.address().trim());
        b.setScheduledAt(r.scheduledAt());
        b.setCity(r.city().trim());
        b.setCategory(category);
        b.setProvider(preferred);
        b = bookings.save(b);           // PENDING: finding a professional
        dispatch.bookingRequested(b);   // same transaction: no booking without its message
        return b;
    }

    @GetMapping("/{id}")
    public Booking get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Booking b = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        // 404 (not 403) for other people's bookings, so IDs can't be probed.
        if (!isAdmin(jwt) && !b.getCustomerPhone().equals(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        return b;
    }

    /** Customers: their own bookings. Admins: any phone via ?phone=. */
    @GetMapping
    public List<Booking> list(@RequestParam(required = false) String phone, @AuthenticationPrincipal Jwt jwt) {
        String target = (isAdmin(jwt) && phone != null && !phone.isBlank()) ? phone : jwt.getSubject();
        return bookings.findByCustomerPhoneOrderByScheduledAtDesc(target);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public Booking updateStatus(@PathVariable Long id, @RequestParam BookingStatus status,
                                @AuthenticationPrincipal Jwt jwt) {
        Booking b = get(id, jwt);
        if (!isAdmin(jwt)) {
            boolean cancellable = b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.CONFIRMED;
            if (status != BookingStatus.CANCELLED || !cancellable) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You can only cancel a booking that is pending or confirmed");
            }
        }
        if (status == BookingStatus.CANCELLED) {
            if (b.getStatus() != BookingStatus.CANCELLED) {
                b.cancel(null);
                dispatch.bookingCancelled(b.getId()); // free the professional's slot
            }
        } else {
            b.setStatus(status);
        }
        return bookings.save(b);
    }

    private static boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
