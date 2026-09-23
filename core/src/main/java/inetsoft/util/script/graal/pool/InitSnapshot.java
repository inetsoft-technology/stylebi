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
package inetsoft.util.script.graal.pool;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.security.OrganizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * What every context of one pooled env is built from (bug #76960, spec §4.1, G9): the org id
 * and the library script sources, taken once when the env is created, so a context created
 * later by a thread carrying another org still has the sandbox's library.
 */
final class InitSnapshot {
   InitSnapshot(String orgId, Map<String, String> library) {
      this.orgId = orgId;
      this.library = Collections.unmodifiableMap(new LinkedHashMap<>(library));
   }

   /**
    * Take a snapshot on the calling thread, which is the thread creating the env. Never throws:
    * without a LibManager (minimal or test contexts) the library is empty, as the engine's own
    * install then skips it.
    */
   static InitSnapshot capture() {
      String orgId = null;

      try {
         orgId = OrganizationManager.getInstance().getCurrentOrgID();
      }
      catch(Throwable ex) {
         LOG.debug("Org id not captured for worksheet script contexts", ex);
      }

      Map<String, String> library = new LinkedHashMap<>();

      try {
         LibManager mgr = LibManagerProvider.getInstance().getManager();
         Enumeration<String> names = mgr.getScripts();

         while(names.hasMoreElements()) {
            String name = names.nextElement();
            String source = mgr.getScript(name);

            if(source != null) {
               library.put(name, source);
            }
         }
      }
      catch(Throwable ex) {
         LOG.debug("Library functions not captured; LibManager unavailable", ex);
      }

      return new InitSnapshot(orgId, library);
   }

   String getOrgId() {
      return orgId;
   }

   Map<String, String> getLibrary() {
      return library;
   }

   private final String orgId;
   private final Map<String, String> library;

   private static final Logger LOG = LoggerFactory.getLogger(InitSnapshot.class);
}
