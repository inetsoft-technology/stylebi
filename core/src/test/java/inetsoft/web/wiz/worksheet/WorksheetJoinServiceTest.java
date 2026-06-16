/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataCacheSweeper;
import org.junit.jupiter.api.*;
import org.springframework.context.support.GenericApplicationContext;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WorksheetJoinServiceTest {
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

   private WorksheetPairingService pairing;
   private WorksheetEngine engine;
   private WorksheetAgentFeature feature;
   private WorksheetJoinService join;
   private RuntimeWorksheet rws;

   @BeforeEach
   void setUp() {
      pairing = new WorksheetPairingService();
      engine = mock(WorksheetEngine.class);
      feature = mock(WorksheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);   // flag ON for happy-path tests
      rws = mock(RuntimeWorksheet.class);
      join = new WorksheetJoinService(pairing, engine, feature);
   }

   private Principal agent(String name, String org) {
      return TestPrincipals.user(name, org);   // same name+org, fresh secureID each call
   }

   @Test
   void validCodeSameLogicalUserGrantsRuntime() throws Exception {
      when(engine.getWorksheetForPairing(eq("Worksheet/foo-7"), any())).thenReturn(rws);
      String code = pairing.mint("Worksheet/foo-7", new IdentityID("alice", "host-org").convertToKey(), "stomp-1");
      RuntimeWorksheet result = join.join(code, agent("alice", "host-org"));
      assertSame(rws, result);
   }

   @Test
   void wrongCodeIsRejected() {
      assertThrows(PairingException.class, () -> join.join("NOPE", agent("alice", "host-org")));
   }

   @Test
   void differentLogicalUserIsRejected() {
      String code = pairing.mint("Worksheet/foo-7", new IdentityID("alice", "host-org").convertToKey(), "stomp-1");
      assertThrows(PairingException.class, () -> join.join(code, agent("bob", "host-org")));
   }

   @Test
   void codeIsConsumedAfterSuccessfulJoin() throws Exception {
      when(engine.getWorksheetForPairing(any(), any())).thenReturn(rws);
      String code = pairing.mint("Worksheet/foo-7", new IdentityID("alice", "host-org").convertToKey(), "stomp-1");
      join.join(code, agent("alice", "host-org"));
      assertThrows(PairingException.class, () -> join.join(code, agent("alice", "host-org")),
                   "code must be single-use even on success");
   }

   @Test
   void nullCodeIsRejected() {
      assertThrows(PairingException.class, () -> join.join(null, agent("alice", "host-org")));
   }

   @Test
   void nullAgentIsRejected() {
      String code = pairing.mint("Worksheet/foo-7", new IdentityID("alice", "host-org").convertToKey(), "stomp-1");
      assertThrows(PairingException.class, () -> join.join(code, null));
   }

   @Test
   void featureFlagOffRejectsJoinAndDoesNotConsumeCode() {
      when(feature.isEnabled()).thenReturn(false);
      String code = pairing.mint("Worksheet/foo-7", new IdentityID("alice", "host-org").convertToKey(), "stomp-1");
      assertThrows(PairingException.class, () -> join.join(code, agent("alice", "host-org")));
      assertNotNull(pairing.peek(code), "join must short-circuit before consuming the code when disabled");
      verifyNoInteractions(engine);
   }
}
