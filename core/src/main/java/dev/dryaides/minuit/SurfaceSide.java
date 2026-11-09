package dev.dryaides.minuit;

/** Which side of its anchor a floating panel sits on, or which edge of its area a drawer comes from. */
public enum SurfaceSide {
    TOP, BOTTOM, LEFT, RIGHT;

    boolean vertical() {
        return this == TOP || this == BOTTOM;
    }

    SurfaceSide opposite() {
        return switch (this) {
            case TOP -> BOTTOM;
            case BOTTOM -> TOP;
            case LEFT -> RIGHT;
            default -> LEFT;
        };
    }
}
