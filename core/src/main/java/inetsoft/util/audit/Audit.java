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
package inetsoft.util.audit;

import inetsoft.util.*;

import java.security.Principal;
import java.util.concurrent.ExecutorService;

/**
 * The {@code Audit} class contains methods used to audit access to the software.
 */
@SingletonManager.Singleton
public class Audit implements AutoCloseable {
   /**
    * Gets the singleton instance of this class.
    *
    * @return an Audit instance.
    */
   public static Audit getInstance() {
      if(implCls == null) {
         try {
            implCls = Class.forName("inetsoft.enterprise.audit.DefaultAudit");
         }
         catch(Exception ex) {
            implCls = Audit.class;
         }
      }
      return (Audit) SingletonManager.getInstance(implCls);
   }

   @Override
   public void close() throws Exception {
   }

   public void auditAction(ActionRecord record, Principal principal) {
   }

   public void auditBookmark(BookmarkRecord record, Principal principal) {
   }

   public void auditExecution(ExecutionRecord record, Principal principal) {
   }

   public void auditExport(ExportRecord record, Principal principal) {
   }

   public void auditIdentityInfo(IdentityInfoRecord record, Principal principal) {
   }

   public void auditQuery(QueryRecord record, Principal principal) {
   }

   public void auditSession(SessionRecord record, Principal principal) {
   }

   private static Class implCls;
}
