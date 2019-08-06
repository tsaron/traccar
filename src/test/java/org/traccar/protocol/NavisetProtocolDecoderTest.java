package org.traccar.protocol;

import org.junit.Test;
import org.traccar.ProtocolTest;

public class NavisetProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        NavisetProtocolDecoder decoder = new NavisetProtocolDecoder(null);

        verifyNull(decoder, binary(
                "1310e4073836383230343030353935383436362a060716"));

    }

}
