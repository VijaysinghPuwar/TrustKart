package com.vijaysinghpuwar.trustkart.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A delivery address as entered by the shopper. Validated for shape only; it may be entirely fictional. */
public record AddressInput(
        @NotBlank @Size(max = 40) String label,
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 80) String region,
        @NotBlank @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]{2,16}", message = "Use letters, digits, spaces or hyphens") String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "Use a two-letter country code") String country) {

    public AddressInput normalized() {
        return new AddressInput(label.strip(), fullName.strip(), line1.strip(), blankToNull(line2), city.strip(),
                blankToNull(region), postalCode.strip().toUpperCase(java.util.Locale.ROOT), country);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
