package uy.edu.um.luminalabs.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SocialNetworkTest {

    @Test
    void buildsProfileUrlFromUsername() {
        assertEquals("https://www.instagram.com/costa.este", SocialNetwork.INSTAGRAM.toUrl("@costa.este"));
        assertEquals("https://www.instagram.com/costa.este", SocialNetwork.INSTAGRAM.toUrl("costa.este"));
        assertEquals("https://www.tiktok.com/@costaeste", SocialNetwork.TIKTOK.toUrl("@costaeste"));
        assertEquals("https://www.facebook.com/costaeste", SocialNetwork.FACEBOOK.toUrl("costaeste"));
    }

    @Test
    void acceptsPastedLinksWithOrWithoutScheme() {
        assertEquals("https://www.instagram.com/costaeste/", SocialNetwork.INSTAGRAM.toUrl("https://www.instagram.com/costaeste/"));
        assertEquals("https://instagram.com/costaeste", SocialNetwork.INSTAGRAM.toUrl("instagram.com/costaeste"));
        assertEquals("https://www.costaeste.com.uy", SocialNetwork.WEBSITE.toUrl("www.costaeste.com.uy"));
        assertEquals("http://costaeste.uy/tours", SocialNetwork.WEBSITE.toUrl("http://costaeste.uy/tours"));
    }

    @Test
    void emptyMeansNoLink() {
        assertNull(SocialNetwork.WEBSITE.toUrl("  "));
        assertNull(SocialNetwork.INSTAGRAM.toUrl(null));
    }

    @Test
    void rejectsLinksOfAnotherNetwork() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> SocialNetwork.INSTAGRAM.toUrl("https://www.facebook.com/costaeste"));
        assertEquals("Ese link no es de Instagram.", e.getMessage());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> SocialNetwork.INSTAGRAM.toUrl("costa este"));
        assertThrows(IllegalArgumentException.class, () -> SocialNetwork.INSTAGRAM.toUrl("@costa!este"));
        assertThrows(IllegalArgumentException.class, () -> SocialNetwork.WEBSITE.toUrl("localhost"));
        // Nunca se guarda algo que ejecute codigo al tocarlo
        assertThrows(IllegalArgumentException.class, () -> SocialNetwork.WEBSITE.toUrl("javascript:alert(1)"));
    }
}
