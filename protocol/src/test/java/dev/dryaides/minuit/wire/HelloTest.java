package dev.dryaides.minuit.wire;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The hello layout of DESIGN.md "Handshake", bytes written by hand from the spec. */
class HelloTest {

    // varint 1, str "1.0.0", varint 1, str "hello/1"
    private static final byte[] CLIENT_1_0 = {
            0x01,
            0x05, '1', '.', '0', '.', '0',
            0x01,
            0x07, 'h', 'e', 'l', 'l', 'o', '/', '1'};

    @Test
    void clientHelloMatchesTheSpecBytes() {
        Hello hello = new Hello(1, "1.0.0", List.of("hello/1"));
        assertArrayEquals(CLIENT_1_0, hello.encode());
        assertEquals(hello, Hello.decode(CLIENT_1_0));
    }

    @Test
    void protocolOver127TakesTwoVarintBytesAndUtf8CountsBytes() {
        // varint 300 = 0xAC 0x02; "é" is 2 UTF-8 bytes; no capabilities
        byte[] bytes = {(byte) 0xAC, 0x02, 0x02, (byte) 0xC3, (byte) 0xA9, 0x00};
        Hello hello = new Hello(300, "é", List.of());
        assertArrayEquals(bytes, hello.encode());
        assertEquals(hello, Hello.decode(bytes));
    }

    @Test
    void serverHelloMatchesTheSpecBytes() {
        // varint 1, varint 1, str "form/1"
        byte[] bytes = {0x01, 0x01, 0x06, 'f', 'o', 'r', 'm', '/', '1'};
        ServerHello hello = new ServerHello(1, List.of("form/1"));
        assertArrayEquals(bytes, hello.encode());
        assertEquals(hello, ServerHello.decode(bytes));
    }

    @Test
    void overCapsAreDropped() {
        // 65 capabilities, one over the cap of 64
        assertThrows(WireException.class, () -> Hello.decode(new byte[]{0x01, 0x00, 0x41}));
        // a version string claiming 129 bytes, over 4 times its 32 character cap
        assertThrows(WireException.class, () -> Hello.decode(new byte[]{0x01, (byte) 0x81, 0x01}));
        // the message ends inside the version
        assertThrows(WireException.class, () -> Hello.decode(new byte[]{0x01, 0x05, '1'}));
    }
}
