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
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.internal.VSUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

/**
 * Component that ensures that open viewsheets are closed when the client that opened them
 * is closed,
 */
@Component
public class RuntimeViewsheetManager {
   @Autowired
   public RuntimeViewsheetManager(ViewsheetService viewsheetService,
                                  @Qualifier("worksheetService") WorksheetService worksheetService)
   {
      this.viewsheetService = viewsheetService;
      this.worksheetService = worksheetService;
   }

   public void sheetOpened(Principal user, String runtimeId) {
      String sessionId = getSessionId(user);

      lock.lock();

      try {
         openSheets.computeIfAbsent(sessionId, k -> new HashSet<>()).add(runtimeId);
      }
      finally {
         lock.unlock();
      }
   }

   public void sheetClosed(Principal user, String runtimeId) {
      String sessionId = getSessionId(user);

      lock.lock();

      try {
         Set<String> sheets = openSheets.get(sessionId);

         if(sheets != null) {
            sheets.remove(runtimeId);

            if(sheets.isEmpty()) {
               openSheets.remove(sessionId);
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   public void sessionEnded(Principal user) {
      // websocket might reconnect right away, wait a little before closing the viewsheets
      VSUtil.getDebouncer().debounce(getDebounceKey(user), 1L, TimeUnit.MINUTES, () -> {
         closeViewsheets(user);
      });
   }

   public void sessionConnected(Principal user) {
      VSUtil.getDebouncer().cancel(getDebounceKey(user));
   }

   private void closeViewsheets(Principal user) {
      String sessionId = getSessionId(user);
      Set<String> sheetsToClose;

      lock.lock();

      try {
         sheetsToClose = openSheets.remove(sessionId);
      }
      finally {
         lock.unlock();
      }

      if(sheetsToClose != null) {
         for(String runtimeId : sheetsToClose) {
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
         }
      }
   }

   private String getSessionId(Principal user) {
      return user != null && user instanceof XPrincipal ? ((XPrincipal) user).getSessionID() : "unknown-session";
   }

   private String getDebounceKey(Principal user) {
      return "RuntimeViewsheetManager." + getSessionId(user);
   }

   private final Map<String, Set<String>> openSheets = new HashMap<>();
         private final Map<String, AssetEntry> openWorksheets = new HashMap<>();
   private final ViewsheetService viewsheetService;
   private final WorksheetService worksheetService;
   private static final Logger LOG = LoggerFactory.getLogger(RuntimeViewsheetManager.class);
   private final Lock lock = new ReentrantLock();
}
