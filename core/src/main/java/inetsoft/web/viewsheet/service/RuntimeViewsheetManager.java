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
import inetsoft.sree.internal.cluster.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.ServiceProxyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

/**
 * Component that ensures that open viewsheets are closed when the client that opened them
 * is closed,
 */
@Component
public class RuntimeViewsheetManager {
   @Autowired
   public RuntimeViewsheetManager(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
      Cluster cluster = Cluster.getInstance();
      openSheets = cluster.getReplicatedMap(OPEN_SHEETS_MAP);
   }

   public void sheetOpened(Principal user, String runtimeId) {
      String sessionId = getSessionId(user);

      openSheets.lock(sessionId);

      try {
         Set<String> sheets = openSheets.computeIfAbsent(sessionId, k -> new HashSet<>());
         sheets.add(runtimeId);
         openSheets.put(sessionId, sheets);
      }
      finally {
         openSheets.unlock(sessionId);
      }
   }

   public void sheetClosed(Principal user, String runtimeId) {
      String sessionId = getSessionId(user);

      openSheets.lock(sessionId);

      try {
         Set<String> sheets = openSheets.get(sessionId);

         if(sheets != null) {
            sheets.remove(runtimeId);

            if(sheets.isEmpty()) {
               openSheets.remove(sessionId);
            }
            else {
               openSheets.put(sessionId, sheets);
            }
         }
      }
      finally {
         openSheets.unlock(sessionId);
      }
   }

   public void sessionEnded(Principal user) {
      closeViewsheets(user);
   }

   private void closeViewsheets(Principal user) {
      String sessionId = getSessionId(user);
      Set<String> sheetsToClose;

      openSheets.lock(sessionId);

      try {
         sheetsToClose = openSheets.remove(sessionId);
      }
      finally {
         openSheets.unlock(sessionId);
      }

      if(sheetsToClose != null) {
         for(String runtimeId : sheetsToClose) {
            viewsheetService.affinityCallAsync(runtimeId, new CloseViewsheetTask(runtimeId, user));
         }
      }
   }

   private String getSessionId(Principal user) {
      return user instanceof XPrincipal ? ((XPrincipal) user).getSessionID() : "unknown-session";
   }

   private final DistributedMap<String, Set<String>> openSheets;
   private final ViewsheetService viewsheetService;
   private static final String OPEN_SHEETS_MAP = RuntimeViewsheetManager.class.getName() + ".openSheetsMap";
   private static final Logger LOG = LoggerFactory.getLogger(RuntimeViewsheetManager.class);

   public static final class CloseViewsheetTask implements AffinityCallable<Void> {
      public CloseViewsheetTask(String runtimeId, Principal user) {
         this.runtimeId = runtimeId;
         this.user = user;
      }

      @Override
      public Void call() throws Exception {
         proxyContext.preprocess();

         try {
            ConfigurationContext configContext = ConfigurationContext.getContext();

            try {
               configContext.getSpringBean(ViewsheetService.class).closeViewsheet(runtimeId, user);
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

            return null;
         }
         finally {
            proxyContext.postprocess();
         }
      }

      private final String runtimeId;
      private final Principal user;
      private final ServiceProxyContext proxyContext = new ServiceProxyContext(false);
   }
}
