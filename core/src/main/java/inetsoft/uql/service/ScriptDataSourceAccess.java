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
package inetsoft.uql.service;

import inetsoft.sree.security.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.script.JavaScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Bug #77539: the data source permission check of a registry access a sheet script makes
 * itself. A dashboard, worksheet or report runs its data sources without a data source
 * permission check: the user's access is decided by the asset. A script, though, can call
 * a Java method that looks up a registry data source by name (with its stored settings) or
 * reads its domain or data model, and hand the result to Java code that connects. Such an
 * access must be one the script's user may make, the same as in the designers.
 *
 * <p>An access is the script's own when the Java code between the script and the registry is
 * data source plumbing only ({@code inetsoft.uql}). An access made while running product code
 * a script called (a worksheet through {@code runQuery}, an assembly's data, a lens), or by
 * any code not running a script, is not checked, as before.
 */
final class ScriptDataSourceAccess {
   private ScriptDataSourceAccess() {
   }

   /**
    * Whether the current thread may make the registry access {@code action} on
    * {@code resource}: true unless the access is a script's own (see the class comment) and
    * the context user lacks the permission.
    */
   static boolean permits(ResourceType type, String resource, ResourceAction action) {
      if(!JavaScriptEngine.isScriptThread() || !isScriptAccess()) {
         return true;
      }

      Principal user = ThreadContext.getContextPrincipal();
      boolean allowed = allows(user, type, resource, action);

      if(!allowed) {
         LOG.warn("A script was refused {} access to the data source {} for {}",
                  action, resource, user == null ? null : user.getName());
      }

      return allowed;
   }

   /**
    * The names of a registry listing the current thread may see: all of them unless the
    * listing is a script's own (see the class comment), and then the ones the context user
    * may read. One stack walk per listing.
    */
   static String[] readable(ResourceType type, String[] names) {
      if(names.length == 0 || !JavaScriptEngine.isScriptThread() || !isScriptAccess()) {
         return names;
      }

      Principal user = ThreadContext.getContextPrincipal();
      String[] result = Arrays.stream(names)
         .filter(name -> allows(user, type, name, ResourceAction.READ))
         .toArray(String[]::new);

      if(result.length < names.length) {
         LOG.warn("A script was refused {} of {} names of a data source listing for {}",
                  names.length - result.length, names.length,
                  user == null ? null : user.getName());
      }

      return result;
   }

   private static boolean allows(Principal user, ResourceType type, String resource,
                                 ResourceAction action)
   {
      try {
         return SecurityEngine.getSecurity().checkPermission(user, type, resource, action);
      }
      catch(Exception ex) {
         // e.g. a user that is not logged in
         LOG.debug("Failed to check the permission of a script on {}", resource, ex);
         return false;
      }
   }

   /** Whether the registry access on this thread is made by a script itself. */
   private static boolean isScriptAccess() {
      return StackWalker.getInstance().walk(
         frames -> isScriptAccess(frames.map(StackWalker.StackFrame::getClassName).iterator()));
   }

   /**
    * Whether the classes of a call stack, innermost first, show an access a script made
    * itself: the first frame that is neither data source plumbing ({@code inetsoft.uql}) nor
    * JDK code is the GraalJS host interop that a script calls Java through. Any other class
    * before it is product code the script called, which makes the access that code's, not
    * the script's. Spring AOP proxy plumbing is skipped: the frames of the proxy's target,
    * inside it, decide.
    */
   static boolean isScriptAccess(Iterator<String> classes) {
      while(classes.hasNext()) {
         String name = classes.next();

         if(name.startsWith("com.oracle.truffle.") || name.startsWith("org.graalvm.")) {
            return true;
         }

         // Bug #78237: the plumbing of a Spring proxy, such as the @Lazy registry XEngine
         // holds. The proxy's target runs inside these frames, so its own frame decides
         if(name.startsWith("org.springframework.aop.") ||
            name.startsWith("org.springframework.cglib."))
         {
            continue;
         }

         if(!name.startsWith("inetsoft.uql.") && !name.startsWith("java.") &&
            !name.startsWith("javax.") && !name.startsWith("jdk.") &&
            !name.startsWith("sun."))
         {
            return false;
         }
      }

      return false;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ScriptDataSourceAccess.class);
}
