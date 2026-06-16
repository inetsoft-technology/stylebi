package inetsoft.web.wiz.worksheet;

import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataCacheSweeper;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WorksheetPairingControllerTest {
   // WorksheetEngine's static initializer creates a DataCache, which resolves a
   // DataCacheSweeper Spring bean at class-init time (Mockito's inline mock maker forces
   // that initialization). Building an SRPrincipal also resolves an XSessionService bean.
   // A minimal application context supplying both must be present before either class is
   // touched.
   private static GenericApplicationContext appContext;

   @BeforeAll
   static void initContext() {
      appContext = new GenericApplicationContext();
      appContext.getBeanFactory().registerSingleton(
         "dataCacheSweeper", mock(DataCacheSweeper.class));
      appContext.getBeanFactory().registerSingleton(
         "xSessionService", new XSessionService());
      appContext.refresh();
      ConfigurationContext.getContext().setApplicationContext(appContext);
   }

   @AfterAll
   static void tearDownContext() {
      ConfigurationContext.getContext().setApplicationContext(null);

      if(appContext != null) {
         appContext.close();
      }
   }

   @Test
   void mintBindsOpenRuntimeToOwnerAndSocketSession() {
      WorksheetPairingService pairing = new WorksheetPairingService();
      WorksheetAgentFeature feature = mock(WorksheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);
      WorksheetPairingController c = new WorksheetPairingController(pairing, feature);
      Principal owner = TestPrincipals.user("alice", "host-org");

      String code = c.mint("Worksheet/foo-7", "stomp-1", owner).code();

      PairingGrant g = pairing.peek(code);
      assertEquals("Worksheet/foo-7", g.runtimeId());
      assertEquals("alice~;~host-org", g.ownerIdentity());
      assertEquals("stomp-1", g.socketSessionId());
   }

   @Test
   void mintRefusedWhenFeatureOff() {
      WorksheetPairingService pairing = new WorksheetPairingService();
      WorksheetAgentFeature feature = mock(WorksheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(false);
      WorksheetPairingController c = new WorksheetPairingController(pairing, feature);
      Principal owner = TestPrincipals.user("alice", "host-org");

      assertThrows(ResponseStatusException.class,
                   () -> c.mint("Worksheet/foo-7", "stomp-1", owner));
   }
}
