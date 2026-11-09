package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AvatarComponentTest {

    /** "___" is a valid Minecraft username with no letter to take: no initials, no crash. */
    @Test
    void separatorOnlyNamesHaveNoInitials() {
        assertEquals("", AvatarComponent.initialsOf("___"));
    }
}
