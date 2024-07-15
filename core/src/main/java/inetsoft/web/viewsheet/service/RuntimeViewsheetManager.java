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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.asset.AssetEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * Component that ensures that open viewsheets are closed when the client that opened them
 * is closed,
 */
@Component
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RuntimeViewsheetManager {
   @Autowired
   public RuntimeViewsheetManager(ViewsheetService viewsheetService,
                                  @Qualifier("worksheetService") WorksheetService worksheetService)
   {
      this.viewsheetService = viewsheetService;
      this.worksheetService = worksheetService;
   }

   public void sheetOpened(String runtimeId) {
      synchronized(openSheets) {
         openSheets.add(runtimeId);
      }
   }

   public void sheetClosed(String runtimeId) {
      synchronized(openSheets) {
         openSheets.remove(runtimeId);
      }
   }

   public void sessionEnded(Principal user) {
      synchronized(openSheets) {
         for(Iterator<String> i = openSheets.iterator(); i.hasNext(); ) {
            String runtimeId = i.next();

            try {
               viewsheetService.closeViewsheet(runtimeId, user);
            }
            catch(ExpiredSheetException expiredException) {
               LOG.debug("Failed to close viewsheet, it is expired: {}", runtimeId);
            }
            catch(Exception e) {
               if(LOG.isDebugEnabled()) {
                  LOG.debug("Failed to close viewsheet: {}", runtimeId, e);
               }
               else {
                  LOG.warn("Failed to close viewsheet: {}, {}", runtimeId, e.getMessage());
               }
            }

            i.remove();
         }
      }
   }

   private final Set<String> openSheets = new HashSet<>();
   private final Map<String, AssetEntry> openWorksheets = new HashMap<>();
   private final ViewsheetService viewsheetService;
   private final WorksheetService worksheetService;
   private static final Logger LOG = LoggerFactory.getLogger(RuntimeViewsheetManager.class);
}
