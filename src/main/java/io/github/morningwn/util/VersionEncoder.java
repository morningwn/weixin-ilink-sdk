package io.github.morningwn.util;

import io.github.morningwn.exception.ILinkException;

/**
 * Encodes semver into iLink client version integer.
 */
public final class VersionEncoder {

    private VersionEncoder() {
    }

    /**
     * Encodes semantic version string by (major << 16) | (minor << 8) | patch.
     *
     * @param semanticVersion semantic version, for example 2.1.6
     * @return decimal string of encoded version
     */
    public static String encode(String semanticVersion) {
        if (semanticVersion == null || semanticVersion.isBlank()) {
            throw new ILinkException("semanticVersion cannot be null or blank");
        }
        String[] parts = semanticVersion.trim().split("\\.");
        if (parts.length != 3) {
            throw new ILinkException("semanticVersion must be in format major.minor.patch");
        }
        int major = parsePart(parts[0], "major");
        int minor = parsePart(parts[1], "minor");
        int patch = parsePart(parts[2], "patch");
        int encoded = (major << 16) | (minor << 8) | patch;
        return Integer.toUnsignedString(encoded);
    }

    private static int parsePart(String value, String label) {
        try {
            int part = Integer.parseInt(value);
            if (part < 0 || part > 255) {
                throw new ILinkException(label + " version part must be between 0 and 255");
            }
            return part;
        } catch (NumberFormatException e) {
            throw new ILinkException("Invalid " + label + " version part: " + value, e);
        }
    }
}
