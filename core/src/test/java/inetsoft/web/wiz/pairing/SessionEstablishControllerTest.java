/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SessionEstablishController} -- D10's login-triggered establish endpoint
 * (design doc section 8.5). Covers charter assertion 2 (opt-in establish returns the right
 * shape), counter-assertion 2 (fully inert when the flag is off -- 403, nothing created), and the
 * idempotency contract {@link WizAuthCallbackController} relies on via {@link
 * SessionEstablishController#establishForOwner}.
 */
@Tag("core")
class SessionEstablishControllerTest {

   private static SheetAgentFeature featureWith(boolean enabled) {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isPortalPairingEnabled()).thenReturn(enabled);
      return feature;
   }

   @Test
   void establishReturnsAJoinResponseShapedBodyWithNoRuntimeYet() {
      SessionEstablishController c =
         new SessionEstablishController(featureWith(true), new SheetSessionService());
      Principal owner = TestPrincipals.user("alice", "host-org");

      SessionEstablishController.JoinResponse response = c.establish(owner);

      assertNotNull(response.sessionToken());
      assertFalse(response.sessionToken().isEmpty());
      assertNull(response.runtimeId());
      assertNull(response.sheetType());
      assertNull(response.editorContext());
      assertNull(response.sheetLabel());
      assertNull(response.concurrentSessionCount());
      assertEquals("alice~;~host-org", response.ownerIdentity());
   }

   @Test
   void establishRefusesWithForbiddenWhenFlagIsOff() {
      SheetSessionService sessions = new SheetSessionService();
      SessionEstablishController c = new SessionEstablishController(featureWith(false), sessions);
      Principal owner = TestPrincipals.user("alice", "host-org");

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> c.establish(owner));

      assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
      assertNull(sessions.findEstablishedDirectly("alice~;~host-org"),
                 "nothing must be created when the flag is off");
   }

   @Test
   void establishForOwnerReturnsNullWhenFlagIsOffInsteadOfThrowing() {
      // WizAuthCallbackController calls establishForOwner directly (in-process) and must be able
      // to treat "flag off" as "no portal session" without a thrown exception derailing the SSO
      // callback's own response -- see that controller's establishPortalSession helper.
      SheetSessionService sessions = new SheetSessionService();
      SessionEstablishController c = new SessionEstablishController(featureWith(false), sessions);

      assertNull(c.establishForOwner("alice~;~host-org"));
      assertNull(sessions.findEstablishedDirectly("alice~;~host-org"));
   }

   @Test
   void establishForOwnerIsIdempotentForTheSameOwner() {
      SheetSessionService sessions = new SheetSessionService();
      SessionEstablishController c = new SessionEstablishController(featureWith(true), sessions);

      SessionEstablishController.JoinResponse first = c.establishForOwner("alice~;~host-org");
      SessionEstablishController.JoinResponse second = c.establishForOwner("alice~;~host-org");

      assertEquals(first.sessionToken(), second.sessionToken(),
                   "a second establish for the same identity must reuse the first session, not " +
                   "mint a codeless duplicate");
   }

   @Test
   void establishMarksTheUnderlyingSessionEstablishedDirectly() {
      SheetSessionService sessions = new SheetSessionService();
      SessionEstablishController c = new SessionEstablishController(featureWith(true), sessions);

      SessionEstablishController.JoinResponse response = c.establishForOwner("alice~;~host-org");

      JoinSession underlying = sessions.resolve(response.sessionToken(), "alice~;~host-org");
      assertNotNull(underlying);
      assertTrue(underlying.establishedDirectly());
   }
}
