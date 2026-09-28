package com.vijaysinghpuwar.trustkart.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An optional, fictional address for the simulation. Only a label and a few free-text lines, deliberately
 * no phone number or anything that would encourage entering real personal data.
 */
public record SimulationAddress(
        @NotBlank @Size(max = 60) String label,
        @Size(max = 80) String line1,
        @Size(max = 60) String city,
        @Size(max = 40) String region,
        @Size(max = 12) @Pattern(regexp = "[A-Za-z0-9 -]*") String postalCode) {}
