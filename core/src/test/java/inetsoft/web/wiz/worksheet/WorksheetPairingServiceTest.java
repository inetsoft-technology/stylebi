package inetsoft.web.wiz.worksheet;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WorksheetPairingServiceTest {
   private long now;
   private WorksheetPairingService svc;

   @BeforeEach
   void setUp() {
      now = 1_000_000L;
      svc = new WorksheetPairingService(() -> now);   // injectable clock
   }

   @Test
   void mintProducesLookupableCode() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      assertNotNull(code);
      PairingGrant g = svc.peek(code);
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-1", g.socketSessionId());
   }

   @Test
   void consumeIsSingleUse() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      assertNotNull(svc.consume(code));
      assertNull(svc.consume(code), "second consume must return null");
   }

   @Test
   void expiredCodeIsNotConsumable() {
      String code = svc.mint("Worksheet/foo-7", "alice~;~host-org", "stomp-1");
      now += WorksheetPairingService.TTL_MILLIS + 1;
      assertNull(svc.consume(code), "expired code must not consume");
   }
}
