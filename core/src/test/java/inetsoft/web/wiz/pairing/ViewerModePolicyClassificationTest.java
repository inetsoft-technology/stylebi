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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A12: the server is the real gate, and it is deny-by-default. This enumerates every
 * {@code @GetMapping/@PostMapping/...} under {@code /api/wiz/v1/agent/**} and fails when one is
 * neither on {@link ViewerModePolicy#ALLOWED} nor classified in {@code viewer-mode-routes.txt}
 * (DENIED = carries a {sessionToken} and is refused for a viewer session; UNGATED = carries none,
 * with the reason in the file). A new endpoint therefore breaks the build until its author decides.
 *
 * <p>To regenerate the file after adding a route, run this test with
 * {@code -Dviewer.routes.dump=<path>} and review the diff.
 */
@Tag("core")
class ViewerModePolicyClassificationTest {
   private static final String RESOURCE = "/inetsoft/web/wiz/pairing/viewer-mode-routes.txt";

   /** Routes the interceptor cannot see because the session token is in the request body; each is
    *  guarded in SheetOpenService (SheetOpenServiceViewerModeTest). */
   private static final Set<String> BODY_TOKEN_OPENERS = Set.of(
      "POST /api/wiz/v1/agent/viewsheet/create",
      "POST /api/wiz/v1/agent/sheet/open",
      "POST /api/wiz/v1/agent/worksheet/create");

   @Test
   void everyAgentRouteIsAllowListedOrClassified() throws Exception {
      Set<String> scanned = scanRoutes();
      dumpIfRequested(scanned);
      Map<String, String> classified = readClassification();
      Set<String> allowed = new TreeSet<>();

      for(ViewerModePolicy.Route r : ViewerModePolicy.ALLOWED) {
         allowed.add(r.method() + " " + r.pattern());
      }

      Set<String> unclassified = new TreeSet<>(scanned);
      unclassified.removeAll(allowed);
      unclassified.removeAll(classified.keySet());
      assertTrue(unclassified.isEmpty(),
                 "Route(s) neither on ViewerModePolicy.ALLOWED nor classified in " + RESOURCE +
                 " (decide: viewer may call it, or it is DENIED/UNGATED): " + unclassified);

      Set<String> stale = new TreeSet<>(classified.keySet());
      stale.removeAll(scanned);
      assertTrue(stale.isEmpty(), "Classified route(s) that no longer exist: " + stale);
   }

   @Test
   void everyAllowListEntryResolvesToARealRoute() throws Exception {
      Set<String> scanned = scanRoutes();

      for(ViewerModePolicy.Route r : ViewerModePolicy.ALLOWED) {
         assertTrue(scanned.contains(r.method() + " " + r.pattern()),
                    "dead allow-list entry: " + r);
      }
   }

   @Test
   void theClassificationAgreesWithWhatTheInterceptorCanSee() throws Exception {
      for(Map.Entry<String, String> e : readClassification().entrySet()) {
         String route = e.getKey();
         boolean hasToken = route.contains("{" + ViewerModePolicy.TOKEN_VARIABLE + "}");
         String[] parts = route.split(" ", 2);

         assertFalse(ViewerModePolicy.isAllowed(parts[0], parts[1]),
                     "classified AND allow-listed: " + route);

         if("DENIED".equals(e.getValue())) {
            assertTrue(hasToken, "DENIED needs a {sessionToken} for the interceptor: " + route);
         }
         else {
            assertEquals("UNGATED", e.getValue(), route);
            assertFalse(hasToken, "UNGATED but it carries a session token: " + route);
         }
      }
   }

   @Test
   void everyRouteWithoutATokenInItsPathIsAKnownUngatedOne() throws Exception {
      for(String route : scanRoutes()) {
         if(!route.contains("{" + ViewerModePolicy.TOKEN_VARIABLE + "}")) {
            assertEquals("UNGATED", readClassification().get(route), route);
         }
      }

      for(String opener : BODY_TOKEN_OPENERS) {
         assertEquals("UNGATED", readClassification().get(opener), opener);
      }
   }

   @Test
   void theNineInteractRoutesAndTheOpenersAreDenied() throws Exception {
      Map<String, String> c = readClassification();
      String vs = "/api/wiz/v1/agent/viewsheet/{sessionToken}";
      List<String> interact = List.of(
         "POST " + vs + "/selection", "POST " + vs + "/selection/clear",
         "POST " + vs + "/selection/subtree", "POST " + vs + "/calendar/dates",
         "POST " + vs + "/calendar/clear", "POST " + vs + "/input/value",
         "POST " + vs + "/submit/click", "POST " + vs + "/bookmarks/goto",
         "POST " + vs + "/parameters", "POST " + vs + "/assembly/max-mode",
         "POST " + vs + "/form-table/insert-row", "POST " + vs + "/form-table/delete-rows",
         "POST " + vs + "/form-table/set-cell", "POST " + vs + "/form-table/apply",
         "POST " + vs + "/save", "POST " + vs + "/open-base-worksheet");

      for(String route : interact) {
         assertEquals("DENIED", c.get(route), route);
      }
   }

   // ---------------------------------------------------------------------------------------

   private static Map<String, String> readClassification() throws Exception {
      Map<String, String> result = new TreeMap<>();

      try(InputStream in = ViewerModePolicyClassificationTest.class.getResourceAsStream(RESOURCE)) {
         assertNotNull(in, RESOURCE + " is missing");
         BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
         String line;

         while((line = r.readLine()) != null) {
            line = line.trim();

            if(line.isEmpty() || line.startsWith("#")) {
               continue;
            }

            String[] p = line.split(" ", 2);
            assertNull(result.put(p[1], p[0]), "duplicate classification: " + line);
         }
      }

      return result;
   }

   private static Set<String> scanRoutes() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
      Set<String> routes = new TreeSet<>();

      for(BeanDefinition def : scanner.findCandidateComponents("inetsoft.web.wiz")) {
         Class<?> type = Class.forName(def.getBeanClassName());
         RequestMapping classMapping = type.getAnnotation(RequestMapping.class);
         String prefix = classMapping == null || classMapping.value().length == 0
            ? "" : classMapping.value()[0];

         for(Method m : type.getDeclaredMethods()) {
            for(String[] mp : mappings(m)) {
               for(String path : mp[0].split("\\|")) {
                  String full = prefix + path;

                  if(full.startsWith(ViewerModePolicy.AGENT_PREFIX)) {
                     routes.add(mp[1] + " " + full);
                  }
               }
            }
         }
      }

      assertTrue(routes.size() > 100, "scan found only " + routes.size() + " agent routes");
      return routes;
   }

   /** Each result: {pipe-joined paths, HTTP method}. */
   private static List<String[]> mappings(Method m) {
      List<String[]> out = new ArrayList<>();
      GetMapping g = m.getAnnotation(GetMapping.class);
      PostMapping p = m.getAnnotation(PostMapping.class);
      PutMapping u = m.getAnnotation(PutMapping.class);
      DeleteMapping d = m.getAnnotation(DeleteMapping.class);
      PatchMapping a = m.getAnnotation(PatchMapping.class);
      RequestMapping r = m.getAnnotation(RequestMapping.class);

      if(g != null) out.add(new String[] { String.join("|", paths(g.value(), g.path())), "GET" });
      if(p != null) out.add(new String[] { String.join("|", paths(p.value(), p.path())), "POST" });
      if(u != null) out.add(new String[] { String.join("|", paths(u.value(), u.path())), "PUT" });
      if(d != null) out.add(new String[] { String.join("|", paths(d.value(), d.path())), "DELETE" });
      if(a != null) out.add(new String[] { String.join("|", paths(a.value(), a.path())), "PATCH" });

      if(r != null) {
         String methods = r.method().length == 0 ? "ANY" : r.method()[0].name();
         out.add(new String[] { String.join("|", paths(r.value(), r.path())), methods });
      }

      return out;
   }

   private static List<String> paths(String[] value, String[] path) {
      return Arrays.asList(value.length > 0 ? value : path);
   }

   private static void dumpIfRequested(Set<String> scanned) throws Exception {
      String target = System.getProperty("viewer.routes.dump");

      if(target == null) {
         return;
      }

      Set<String> allowed = new TreeSet<>();

      for(ViewerModePolicy.Route r : ViewerModePolicy.ALLOWED) {
         allowed.add(r.method() + " " + r.pattern());
      }

      List<String> lines = new ArrayList<>();
      lines.add("# Classification of every /api/wiz/v1/agent/** route that is NOT on ViewerModePolicy.ALLOWED.");
      lines.add("# DENIED  = carries {sessionToken}; ViewerModeInterceptor refuses it for a viewer session.");
      lines.add("# UNGATED = carries no session token in its path (join/peek/signature/vocabulary/asset-exists)");
      lines.add("#           or has it in the body (the three openers, guarded in SheetOpenService).");
      lines.add("# Maintained by ViewerModePolicyClassificationTest; see its javadoc to regenerate.");

      for(String route : scanned) {
         if(!allowed.contains(route)) {
            lines.add((route.contains("{sessionToken}") ? "DENIED " : "UNGATED ") + route);
         }
      }

      Files.write(Path.of(target), lines, StandardCharsets.UTF_8);
   }
}
