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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Pairing-authorized runtime access. Bypasses the per-session SRPrincipal check in
 * WorksheetEngine/ViewsheetEngine.getSheet(). The caller (SheetJoinService) is responsible
 * for verifying the agent is the same logical user via a valid PairingGrant.
 *
 * <p>Uses SheetDirectAccessor (implemented by WorksheetEngine) so that unit tests can
 * mock the thin interface without triggering WorksheetEngine's static initializers.
 */
@Service
public class SheetRuntimeAccess {
   private static final Logger LOG = LoggerFactory.getLogger(SheetRuntimeAccess.class);

   private final SheetDirectAccessor worksheetAccessor;
   private final SheetDirectAccessor viewsheetAccessor;

   /**
    * Production constructor — Spring injects WorksheetService (WorksheetEngine) and
    * ViewsheetService (ViewsheetEngine), both of which implement SheetDirectAccessor.
    */
   @Autowired
   public SheetRuntimeAccess(WorksheetService worksheetService,
                              ViewsheetService viewsheetService)
   {
      this.worksheetAccessor = (SheetDirectAccessor) worksheetService;
      this.viewsheetAccessor = (SheetDirectAccessor) viewsheetService;
   }

   /**
    * Package-private constructor for unit tests — accepts SheetDirectAccessor mocks directly.
    */
   SheetRuntimeAccess(SheetDirectAccessor worksheetAccessor,
                       SheetDirectAccessor viewsheetAccessor)
   {
      this.worksheetAccessor = worksheetAccessor;
      this.viewsheetAccessor = viewsheetAccessor;
   }

   /**
    * Fetch the RuntimeSheet for pairing-authorized access, bypassing matches().
    * Touches the runtime (access(true)) and audits the access.
    *
    * @throws PairingException if the runtime is not found or wrong type for the sheetType.
    */
   public RuntimeSheet getSheetForPairing(SheetType sheetType, String runtimeId,
                                           Principal agentUser)
      throws PairingException
   {
      return switch (sheetType) {
         case WORKSHEET -> getWorksheetForPairing(runtimeId, agentUser);
         case VIEWSHEET -> getViewsheetForPairing(runtimeId, agentUser);
      };
   }

   private RuntimeWorksheet getWorksheetForPairing(String runtimeId, Principal agentUser)
      throws PairingException
   {
      RuntimeSheet rs = worksheetAccessor.getSheetDirect(runtimeId);

      if(rs == null) {
         LOG.warn("Pairing runtime access: worksheet runtime not found (id={}, user={})",
                  runtimeId, agentUser.getName());
         throw new PairingException("Worksheet runtime not found or expired: " + runtimeId);
      }

      if(!(rs instanceof RuntimeWorksheet rws)) {
         LOG.warn("Pairing runtime access: not a worksheet runtime (id={}, user={})",
                  runtimeId, agentUser.getName());
         throw new PairingException("Worksheet runtime not found or expired: " + runtimeId);
      }

      rs.access(true);
      LOG.info("Pairing worksheet runtime access granted (id={}, agent={})",
               runtimeId, agentUser.getName());
      return rws;
   }

   private RuntimeViewsheet getViewsheetForPairing(String runtimeId, Principal agentUser)
      throws PairingException
   {
      RuntimeSheet rs = viewsheetAccessor.getSheetDirect(runtimeId);

      if(rs == null) {
         LOG.warn("Pairing runtime access: viewsheet runtime not found (id={}, user={})",
                  runtimeId, agentUser.getName());
         throw new PairingException("Viewsheet runtime not found or expired: " + runtimeId);
      }

      if(!(rs instanceof RuntimeViewsheet rvs)) {
         LOG.warn("Pairing runtime access: not a viewsheet runtime (id={}, user={})",
                  runtimeId, agentUser.getName());
         throw new PairingException("Viewsheet runtime not found or expired: " + runtimeId);
      }

      rs.access(true);
      LOG.info("Pairing viewsheet runtime access granted (id={}, agent={})",
               runtimeId, agentUser.getName());
      return rvs;
   }
}
