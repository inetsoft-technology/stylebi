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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * D10's login-triggered establish endpoint (design doc section 8.5) -- establishes a portal-
 * scoped {@link JoinSession} directly from a verified JWT, with no pairing code and no runtime.
 *
 * <p>Deliberately its own file rather than an addition to {@link SheetPairingController}: keeping
 * the two separate avoids a file conflict with a different, deferred feature (Lane C / D9) that
 * also adds endpoints to that controller.
 *
 * <p>Unlike {@link SheetJoinService#join}, there is no code to guess and therefore no throttle/
 * lockout here -- the caller's JWT (verified by {@code WizServiceAuthenticationFilter} before this
 * controller is ever reached, exactly like every other {@code /v1/agent/...} endpoint) is already
 * the proof of identity.
 */
@RestController
public class SessionEstablishController {
   @Autowired
   public SessionEstablishController(SheetAgentFeature feature, SheetSessionService sessions) {
      this.feature = feature;
      this.sessions = sessions;
   }

   /**
    * {@code JoinResponse}-shaped body, matching the shape the pairing-code join endpoints already
    * return (see e.g. {@code WorksheetAgentController.JoinResponse}) -- {@code runtimeId}/
    * {@code sheetType}/{@code editorContext}/{@code sheetLabel}/{@code concurrentSessionCount} are
    * all {@code null} for a freshly (or already) established, unattached session (design doc
    * section 8.5).
    */
   public record JoinResponse(String sessionToken, String runtimeId, String ownerIdentity,
                              String sheetType, EditorContext editorContext, String sheetLabel,
                              Integer concurrentSessionCount) {}

   /**
    * {@code POST /api/wiz/v1/agent/session/establish}. Bearer-JWT authenticated, no request body
    * -- the caller's own identity is the only input. 403 when the portal-pairing flag is off,
    * checked first, before anything else is created.
    */
   @PostMapping("/api/wiz/v1/agent/session/establish")
   public JoinResponse establish(Principal user) {
      JoinResponse response = establishForOwner(PairingUtil.identityKey(user));

      if(response == null) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Portal-session pairing is disabled");
      }

      return response;
   }

   /**
    * The shared establish logic -- callable in-process by {@link
    * inetsoft.web.wiz.controller.WizAuthCallbackController} (same JVM, already holding a JWT-
    * verified identity from the SSO callback) without a second HTTP round trip through this
    * controller's own endpoint, so there is exactly one implementation of "look up or open a
    * directly-established session" to review and test (design doc section 8.2).
    *
    * <p>Returns {@code null}, and never throws, when the portal-pairing flag is off -- the
    * callback handler must treat that as "no portal session; stash the login exactly as today"
    * rather than fail the whole SSO callback (design doc section 8.2, "what happens when the flag
    * is off").
    */
   public JoinResponse establishForOwner(String ownerIdentity) {
      if(!feature.isPortalPairingEnabled()) {
         return null;
      }

      JoinSession session = sessions.openEstablishedDirectly(ownerIdentity);
      String sheetType = session.sheetType() == null ? null : session.sheetType().name().toLowerCase();
      return new JoinResponse(session.sessionToken(), session.runtimeId(), session.ownerIdentity(),
                              sheetType, session.editorContext(), null, null);
   }

   private final SheetAgentFeature feature;
   private final SheetSessionService sessions;
}
