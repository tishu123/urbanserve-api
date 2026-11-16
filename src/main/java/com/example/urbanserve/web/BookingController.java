package com.example.urbanserve.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.urbanserve.dispatch.DispatchOutbox;
import com.example.urbanserve.model.Booking;
import com.example.urbanserve.model.BookingItem;
import com.example.urbanserve.model.BookingStatus;
import com.example.urbanserve.model.Provider;
import com.example.urbanserve.model.ServiceCategory;
import com.example.urbanserve.model.ServicePackage;
import com.example.urbanserve.repo.BookingRepository;
import com.example.urbanserve.repo.CategoryRepository;
import com.example.urbanserve.repo.ProviderRepository;
import com.example.urbanserve.repo.ServicePackageRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    public record BookingItemRequest(
            @NotBlank @Size(max = 80) String packageCode,
            @NotNull @Min(1) @Max(99) @Digits(integer = 2, fraction = 0)
            BigDecimal quantity) {}

    public record BookingRequest(
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Size(max = 500) String address,
            @NotNull @Future LocalDateTime scheduledAt,
            @NotNull Long categoryId,
            @NotBlank @Size(max = 80) String city,
            Long providerId,
            @Size(min = 1, max = 22)
            List<@NotNull @Valid BookingItemRequest> items) {}

    private final BookingRepository bookings;
    private final CategoryRepository categories;
    private final ProviderRepository providers;
    private final DispatchOutbox dispatch;
    private final ServicePackageRepository packages;

    public BookingController(BookingRepository bookings,
                             CategoryRepository categories,
                             ProviderRepository providers,
                             DispatchOutbox dispatch,
                             ServicePackageRepository packages) {
        this.bookings = bookings;
        this.categories = categories;
        this.providers = providers;
        this.dispatch = dispatch;
        this.packages = packages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Booking create(@Valid @RequestBody BookingRequest r,
                          @AuthenticationPrincipal Jwt jwt) {
        ServiceCategory category = categories.findById(r.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));

        Provider preferred = null;
        if (r.providerId() != null) {
            preferred = providers.findById(r.providerId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Provider not found"));

            if (!preferred.getCategory().getId().equals(category.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Provider does not offer this service");
            }
        }

        Booking booking = new Booking();
        booking.setCustomerName(r.customerName().trim());
        booking.setCustomerPhone(jwt.getSubject());
        booking.setAddress(r.address().trim());
        booking.setScheduledAt(r.scheduledAt());
        booking.setCity(r.city().trim());
        booking.setCategory(category);
        booking.setProvider(preferred);

        if (r.items() != null) {
            applyPriceSnapshot(booking, category, r.items());
        }

        booking = bookings.save(booking);
        dispatch.bookingRequested(booking);
        return booking;
    }

    private void applyPriceSnapshot(Booking booking,
                                    ServiceCategory category,
                                    List<BookingItemRequest> requestedItems) {
        List<BookingItem> pricedItems = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        long totalPaise = 0;

        for (BookingItemRequest item : requestedItems) {
            String code = item.packageCode();

            if (!seenCodes.add(code)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Duplicate package: " + code);
            }

            ServicePackage pkg = packages
                    .findByCategory_IdAndCodeAndActiveTrue(category.getId(), code)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Package is unavailable for this category: " + code));

            int quantity = item.quantity().intValueExact();
            long unitPaise = pkg.priceInRupees(category.getBasePrice())
                    .movePointRight(2)
                    .longValueExact();

            totalPaise = Math.addExact(totalPaise, Math.multiplyExact(unitPaise, quantity));
            pricedItems.add(new BookingItem(pkg.getCode(), pkg.getName(), quantity, unitPaise));
        }

        booking.setPriceSnapshot(pricedItems, totalPaise);
    }

    @GetMapping("/{id}")
    public Booking get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Booking b = bookings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));

        if (!isAdmin(jwt) && !b.getCustomerPhone().equals(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        return b;
    }

    @GetMapping
    public List<Booking> list(@RequestParam(required = false) String phone,
                              @AuthenticationPrincipal Jwt jwt) {
        String target = (isAdmin(jwt) && phone != null && !phone.isBlank())
                ? phone
                : jwt.getSubject();

        return bookings.findByCustomerPhoneOrderByScheduledAtDesc(target);
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public Booking updateStatus(@PathVariable Long id,
                                @RequestParam BookingStatus status,
                                @AuthenticationPrincipal Jwt jwt) {
        Booking b = get(id, jwt);

        if (!isAdmin(jwt)) {
            boolean cancellable = b.getStatus() == BookingStatus.PENDING
                    || b.getStatus() == BookingStatus.CONFIRMED;

            if (status != BookingStatus.CANCELLED || !cancellable) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You can only cancel a booking that is pending or confirmed");
            }
        }

        if (status == BookingStatus.CANCELLED) {
            if (b.getStatus() != BookingStatus.CANCELLED) {
                b.cancel(null);
                dispatch.bookingCancelled(b.getId());
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