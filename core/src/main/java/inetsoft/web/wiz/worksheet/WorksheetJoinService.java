package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.security.Principal;

@Service
public class WorksheetJoinService {
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetJoinService.class);
   private final WorksheetPairingService pairing;
   private final WorksheetEngine engine;
   private final WorksheetAgentFeature feature;

   public WorksheetJoinService(WorksheetPairingService pairing, WorksheetEngine engine,
                               WorksheetAgentFeature feature)
   {
      this.pairing = pairing;
      this.engine = engine;
      this.feature = feature;
   }

   /** Validate flag + code + same-logical-user, consume it, return the shared runtime. */
   public RuntimeWorksheet join(String code, Principal agentUser) throws PairingException {
      if(!feature.isEnabled()) {
         LOG.warn("Worksheet agent pairing join rejected: feature disabled (user={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Worksheet agent pairing is disabled");
      }

      PairingGrant grant = pairing.consume(code);

      if(grant == null) {
         LOG.warn("Worksheet agent pairing join rejected: invalid/expired code (user={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Invalid or expired pairing code");
      }

      if(!sameLogicalUser(grant.ownerIdentity(), agentUser)) {
         LOG.warn("Worksheet agent pairing join rejected: user mismatch (owner={}, agent={})",
                  grant.ownerIdentity(), agentUser == null ? "?" : agentUser.getName());
         throw new PairingException("Pairing code does not belong to this user");
      }

      LOG.info("Worksheet agent pairing join granted (runtime={}, user={})",
               grant.runtimeId(), agentUser.getName());
      return engine.getWorksheetForPairing(grant.runtimeId(), agentUser);
   }

   private boolean sameLogicalUser(String ownerIdentity, Principal agentUser) {
      if(!(agentUser instanceof XPrincipal p)) {
         return false;
      }

      IdentityID agentId = IdentityID.getIdentityIDFromKey(p.getName());
      return ownerIdentity.equals(agentId == null ? p.getName() : agentId.convertToKey());
   }
}
