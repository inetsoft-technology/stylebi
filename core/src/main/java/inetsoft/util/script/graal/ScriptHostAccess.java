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
package inetsoft.util.script.graal;

import inetsoft.sree.SreeEnv;
import org.graalvm.polyglot.HostAccess;
import java.util.*;
import java.util.function.Predicate;

/**
 * Single audit point for script Java interop security. Builds the HostAccess
 * member policy (annotation-driven + curated target-type mappings) and the
 * class-lookup allow-list that gates Java.type(...).
 */
public final class ScriptHostAccess {
   private ScriptHostAccess() {
   }

   // Tier 1: safe java.* utilities. Finalized by audit in Task 6.4.
   private static final Set<String> ALLOWED_CLASSES = Set.of(
      "java.lang.Math", "java.lang.String",
      "java.lang.Integer", "java.lang.Long", "java.lang.Double", "java.lang.Boolean",
      "java.text.SimpleDateFormat", "java.text.DecimalFormat",
      "java.util.Date", "java.util.Calendar", "java.util.ArrayList",
      "java.util.HashMap", "java.util.List", "java.util.Arrays"
   );

   // Tier 2: our own API package prefixes.
   private static final List<String> ALLOWED_PREFIXES = List.of(
      "inetsoft.graph.", "inetsoft.report.", "inetsoft.uql."
   );

   private static volatile HostAccess hostAccess;

   public static HostAccess hostAccess() {
      if(hostAccess == null) {
         synchronized(ScriptHostAccess.class) {
            if(hostAccess == null) {
               hostAccess = HostAccess.newBuilder()
                  // allow @Export-annotated instance members and all public access
                  // on class-filter-allowed types (e.g. Java.type('java.lang.Math').max)
                  .allowAccessAnnotatedBy(HostAccess.Export.class)
                  .allowPublicAccess(true)
                  .allowArrayAccess(true)
                  .allowListAccess(true)
                  .allowMapAccess(true)
                  .allowIterableAccess(true)
                  .allowIteratorAccess(true)
                  // legacy convenience: scripts pass JS numbers to Java APIs
                  .targetTypeMapping(Double.class, Integer.class,
                                     d -> d != null && d == Math.floor(d) && !d.isInfinite(),
                                     Double::intValue)
                  .build();
            }
         }
      }

      return hostAccess;
   }

   public static Predicate<String> classFilter() {
      // optional SreeEnv extension (off by default): comma-separated extra FQCNs
      String extraProp = null;
      try {
         extraProp = SreeEnv.getProperty("script.java.allowed.classes");
      }
      catch(Exception ignore) {
         // SreeEnv may be unavailable outside of a full server context
      }
      Set<String> extra = parseExtra(extraProp);

      return fqcn -> {
         if(ALLOWED_CLASSES.contains(fqcn) || extra.contains(fqcn)) {
            return true;
         }

         for(String prefix : ALLOWED_PREFIXES) {
            if(fqcn.startsWith(prefix)) {
               return true;
            }
         }

         return false;
      };
   }

   private static Set<String> parseExtra(String prop) {
      if(prop == null || prop.isBlank()) {
         return Set.of();
      }

      Set<String> s = new HashSet<>();

      for(String part : prop.split(",")) {
         String t = part.trim();

         if(!t.isEmpty()) {
            s.add(t);
         }
      }

      return s;
   }
}
