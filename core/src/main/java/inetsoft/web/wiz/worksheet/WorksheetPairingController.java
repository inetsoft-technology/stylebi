package inetsoft.web.wiz.worksheet;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@RestController
public class WorksheetPairingController {
   private final WorksheetPairingService pairing;
   private final WorksheetAgentFeature feature;

   public WorksheetPairingController(WorksheetPairingService pairing,
                                     WorksheetAgentFeature feature)
   {
      this.pairing = pairing;
      this.feature = feature;
   }

   /** Browser mints a code for its open runtime. socketSessionId comes from its STOMP session. */
   public record MintResponse(String code) {}

   @PostMapping("/api/wiz/worksheet/pairing/mint")
   public MintResponse mint(@RequestParam String runtimeId,
                            @RequestParam String socketSessionId,
                            Principal owner)
   {
      if(!feature.isEnabled()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Worksheet agent pairing is disabled");
      }
      String ownerId = owner instanceof XPrincipal
         ? IdentityID.getIdentityIDFromKey(owner.getName()).convertToKey()
         : owner.getName();
      return new MintResponse(pairing.mint(runtimeId, ownerId, socketSessionId));
   }
}
