package com.vijaysinghpuwar.trustkart.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a virtual order was "delivered", snapshotted onto the purchase. It may be entirely fictional, and for
 * preset destinations only the label is set. Nothing is ever shipped to it.
 */
public record SimulationAddress(
        @NotBlank @Size(max = 60) String label,
        @Size(max = 100) String fullName,
        @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @Size(max = 80) String city,
        @Size(max = 80) String region,
        @Size(max = 16) @Pattern(regexp = "[A-Za-z0-9 -]*") String postalCode,
        @Pattern(regexp = "([A-Z]{2})?") String country) {}
