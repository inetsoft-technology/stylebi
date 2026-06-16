package inetsoft.web.wiz.worksheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PairingGrantTest {
   @Test
   void exposesBoundFieldsAndExpiry() {
      PairingGrant g = new PairingGrant(
         "ABC123", "Worksheet/foo-7", "alice~;~host-org", "stomp-sess-1", 1000L, 60_000L);
      assertEquals("ABC123", g.code());
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-sess-1", g.socketSessionId());
      assertFalse(g.isExpired(1000L + 59_999L));
      assertTrue(g.isExpired(1000L + 60_001L));
   }
}
