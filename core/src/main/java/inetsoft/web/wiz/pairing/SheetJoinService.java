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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.security.Principal;

/**
 * Pairing-authorized join. Validates the feature flag, consumes the single-use pairing code,
 * verifies the agent is the same logical user as the runtime owner, and opens a reusable JoinSession.
 *
 * Security contract:
 * - Feature flag checked FIRST — code is never consumed when the capability is disabled.
 * - Code is single-use (consumed by SheetPairingService.consume).
 * - Same-logical-user enforced via PairingUtil.sameLogicalUser (IdentityID name+org match).
 * - Every join attempt (success + failure) is audit-logged.
 */
@Service
public class SheetJoinService {
   private static final Logger LOG = LoggerFactory.getLogger(SheetJoinService.class);

   @Autowired
   public SheetJoinService(SheetPairingService pairing,
                           SheetSessionService sessions,
                           SheetAgentFeature feature) {
      this.pairing = pairing;
      this.sessions = sessions;
      this.feature = feature;
   }

   /**
    * Validate flag + code + same-logical-user, consume it, open and return a reusable JoinSession.
    *
    * @param code      the single-use pairing code the agent presents
    * @param agentUser the agent's authenticated principal
    * @throws PairingException if the flag is off, code is invalid/expired, or user doesn't match
    */
   public JoinSession join(String code, Principal agentUser) throws PairingException {
      // 1. Gate FIRST: do NOT consume the code when the capability is disabled.
      if(!feature.isEnabled()) {
         LOG.warn("Sheet agent pairing join rejected: feature disabled (agent={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Sheet agent pairing is disabled");
      }

      // 2. Consume the code (single-use).
      PairingGrant grant = pairing.consume(code);
      if(grant == null) {
         LOG.warn("Sheet agent pairing join rejected: invalid/expired code (agent={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Invalid or expired pairing code");
      }

      // 3. Same-logical-user check.
      if(!PairingUtil.sameLogicalUser(grant.ownerIdentity(), agentUser)) {
         LOG.warn("Sheet agent pairing join rejected: user mismatch (owner={}, agent={})",
                  grant.ownerIdentity(), agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Pairing code does not belong to this user");
      }

      // 4. Open a reusable session.
      JoinSession session = sessions.open(grant.runtimeId(), grant.ownerIdentity(), grant.sheetType());
      LOG.info("Sheet agent pairing join granted (runtimeId={}, sheetType={}, agent={})",
               grant.runtimeId(), grant.sheetType(), agentUser.getName());
      return session;
   }

   private final SheetPairingService pairing;
   private final SheetSessionService sessions;
   private final SheetAgentFeature feature;
}
