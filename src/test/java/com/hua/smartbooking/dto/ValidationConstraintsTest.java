package com.hua.smartbooking.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationConstraintsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void roomDtoRejectsBlankName() {
        RoomDTO dto = new RoomDTO();
        dto.setName("");
        dto.setCapacity(5);

        Set<ConstraintViolation<RoomDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void roomDtoRejectsCapacityBelowOne() {
        RoomDTO dto = new RoomDTO();
        dto.setName("Conference Room A");
        dto.setCapacity(0);

        Set<ConstraintViolation<RoomDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("capacity"));
    }

    @Test
    void roomDtoRejectsNullCapacity() {
        RoomDTO dto = new RoomDTO();
        dto.setName("Conference Room A");
        dto.setCapacity(null);

        Set<ConstraintViolation<RoomDTO>> violations = validator.validate(dto);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("capacity"));
    }

    @Test
    void roomDtoAcceptsValidInput() {
        RoomDTO dto = new RoomDTO();
        dto.setName("Conference Room A");
        dto.setCapacity(10);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void bookingRequestRejectsDurationBelow15Minutes() {
        BookingRequest request = new BookingRequest();
        request.setDurationMinutes(10);
        request.setDateRangeStart("2026-09-10");
        request.setDateRangeEnd("2026-09-11");

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("durationMinutes"));
    }

    @Test
    void bookingRequestRejectsBlankDateRange() {
        BookingRequest request = new BookingRequest();
        request.setDurationMinutes(30);
        request.setDateRangeStart("");
        request.setDateRangeEnd("");

        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .contains("dateRangeStart", "dateRangeEnd");
    }

    @Test
    void inviteRequestRejectsInvalidEmailFormat() {
        InviteRequest request = new InviteRequest();
        request.setEmail("not-an-email");

        Set<ConstraintViolation<InviteRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void inviteRequestRejectsBlankEmail() {
        InviteRequest request = new InviteRequest();
        request.setEmail("");

        Set<ConstraintViolation<InviteRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void inviteRequestAcceptsValidEmail() {
        InviteRequest request = new InviteRequest();
        request.setEmail("jane@hua.gr");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void roleUpdateRequestRejectsBlankRole() {
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRole("");

        Set<ConstraintViolation<RoleUpdateRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("role"));
    }
}