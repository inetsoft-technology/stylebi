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
package inetsoft.uql.rest.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.wiz.model.WizEndpointCatalog;
import inetsoft.web.wiz.model.WizEndpointCatalogEntry;
import inetsoft.web.wiz.model.WizEndpointLookup;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every connector's {@code endpoints.json} must parse with the mapper the loader actually uses.
 *
 * <p>This guards a failure that is silent by construction. The mapper is a bare
 * {@code new ObjectMapper()}, so FAIL_ON_UNKNOWN_PROPERTIES is on, and {@code AbstractEndpoint}
 * carries no {@code @JsonIgnoreProperties}. A key with no matching property therefore throws — and
 * {@code Endpoints.load} catches that as an IOException and returns an EMPTY map. The connector
 * keeps starting, keeps being offered in the UI, and simply has no endpoints: one LOG.error, and a
 * blank dropdown.</p>
 *
 * <p>Nothing else catches it. The per-connector query tests are tagged {@code endpoints}, which the
 * build excludes because they call the vendor's live API, so in a normal run they contribute zero
 * cases. That left roughly 65 resource files edited by hand with no test standing behind them.</p>
 */
class EndpointsJsonLoadableTest {
   @Test
   void everyConnectorEndpointsFileParses() throws Exception {
      List<Path> roots = findDatasourceRoots();
      List<Path> files = findEndpointFiles(roots);

      // A wrong root or a renamed package would leave this empty and let the test "pass" while
      // checking nothing -- the one outcome this test must never report as success. The resolved
      // locations are in the message because "found none" is otherwise indistinguishable from
      // "looked in the wrong place".
      assertFalse(files.isEmpty(),
                  "found no endpoints.json under " + DATASOURCE_PACKAGE + " (searched " + roots + ")");

      ObjectMapper mapper = EndpointJsonQuery.Endpoints.createObjectMapper();
      List<String> failures = new ArrayList<>();

      for(Path file : files) {
         Path dir = file.getParent();
         String connector = dir.getFileName().toString();
         Class<?> endpointsClass = findEndpointsClass(dir);

         // Each connector binds to its OWN Endpoints subclass: 15 of them declare extra properties
         // (pageType, post, pagePath, pageRequiredParameter) that the shared base class knows
         // nothing about, so parsing them all through AbstractEndpoint would report failures that
         // production never sees.
         if(endpointsClass == null) {
            failures.add(connector + ": no *Endpoints class found beside endpoints.json");
            continue;
         }

         // Parsed through the loader's own mapper but without going through load() itself: that
         // method reads SreeEnv, which needs a Spring context this test has no reason to stand up,
         // and it converts the very failure being hunted here into an empty map. Reading directly
         // lets the exception surface with the offending property named.
         try(InputStream input = Files.newInputStream(file)) {
            Object parsed = mapper.readValue(input, endpointsClass);
            @SuppressWarnings("rawtypes")
            List list = ((EndpointJsonQuery.Endpoints) parsed).getEndpoints();

            if(list == null || list.isEmpty()) {
               failures.add(connector + ": parsed but declares no endpoints");
            }
         }
         catch(Exception e) {
            failures.add(connector + ": " + e.getMessage());
         }
      }

      assertTrue(failures.isEmpty(),
                 "endpoints.json failed to parse for " + failures.size() + " connector(s):\n  "
                    + String.join("\n  ", failures));
   }

   /**
    * The same 65 files must ALSO parse through the wiz catalog DTO, which has the opposite
    * requirement to the loader above: it binds a subset of the properties and must therefore
    * IGNORE the rest. Fifteen connectors declare extras of their own (pageType, post, bodyTemplate,
    * pageLimit, pagePath, pageRequiredParameter, paginationPath, freePageLimit, url); a strict
    * mapper here would take those connectors' catalogs to zero while the loader stayed green.
    */
   @Test
   void everyConnectorEndpointsFileParsesAsCatalog() throws Exception {
      List<Path> roots = findDatasourceRoots();
      List<Path> files = findEndpointFiles(roots);

      assertFalse(files.isEmpty(),
                  "found no endpoints.json under " + DATASOURCE_PACKAGE + " (searched " + roots + ")");

      ObjectMapper mapper = new ObjectMapper();
      List<String> failures = new ArrayList<>();

      // name is a poor guard against a renamed or misspelled JSON key: it is the one field every
      // one of the 4035 entries across all 65 files always carries, and the portal's Tier 1
      // retrieval does not depend on it. A binding failure on suffix, description, or lookups --
      // the fields retrieval actually uses -- would null them out for every connector while this
      // check alone stayed green. The per-field checks below close that gap.
      boolean sawStripeLookupWithEndpointsArray = false;
      boolean sawGithubLookupWithSingularEndpoint = false;

      for(Path file : files) {
         String connector = file.getParent().getFileName().toString();

         try(InputStream input = Files.newInputStream(file)) {
            WizEndpointCatalog catalog = mapper.readValue(input, WizEndpointCatalog.class);

            if(catalog.endpoints() == null || catalog.endpoints().isEmpty()) {
               failures.add(connector + ": parsed but declares no endpoints");
               continue;
            }

            boolean hasSuffix = false;
            boolean hasDescription = false;

            for(WizEndpointCatalogEntry entry : catalog.endpoints()) {
               if(entry.name() == null || entry.name().isBlank()) {
                  failures.add(connector + ": an entry has no name");
                  break;
               }
            }

            for(WizEndpointCatalogEntry entry : catalog.endpoints()) {
               if(entry.suffix() != null && !entry.suffix().isBlank()) {
                  hasSuffix = true;
               }

               if(entry.description() != null && !entry.description().isBlank()) {
                  hasDescription = true;
               }

               if(entry.lookups() != null) {
                  for(WizEndpointLookup lookup : entry.lookups()) {
                     if("stripe".equals(connector) && lookup.endpoints() != null
                        && !lookup.endpoints().isEmpty())
                     {
                        sawStripeLookupWithEndpointsArray = true;
                     }

                     if("github".equals(connector) && lookup.endpoint() != null
                        && !lookup.endpoint().isBlank())
                     {
                        sawGithubLookupWithSingularEndpoint = true;
                     }
                  }
               }
            }

            // Every one of the 65 files has a non-blank suffix on at least one entry; a renamed or
            // misspelled "suffix" JSON key would null it out everywhere while the name-only check
            // above stayed green.
            if(!hasSuffix) {
               failures.add(connector + ": no entry has a non-blank suffix");
            }

            // The description backfill has in fact reached every connector: every entry in every
            // one of the 65 files already carries a non-blank description, so this is asserted for
            // all of them rather than only stripe and github.
            if(!hasDescription) {
               failures.add(connector + ": no entry has a non-blank description");
            }
         }
         catch(Exception e) {
            failures.add(connector + ": " + e.getMessage());
         }
      }

      // Both lookup spellings are exercised in the wild -- Stripe/Zendesk write an "endpoints"
      // array, GitHub writes a singular "endpoint" -- and WizEndpointLookup carries both fields as
      // written (see its javadoc). A binding regression on either field would only show up here,
      // never in the entry-level checks above.
      if(!sawStripeLookupWithEndpointsArray) {
         failures.add("stripe: no lookup bound a non-empty \"endpoints\" array");
      }

      if(!sawGithubLookupWithSingularEndpoint) {
         failures.add("github: no lookup bound a non-blank singular \"endpoint\"");
      }

      assertTrue(failures.isEmpty(),
                 "endpoints.json failed to parse as a catalog for " + failures.size()
                    + " connector(s):\n  " + String.join("\n  ", failures));
   }

   /**
    * Every classpath entry carrying this package, not just the first.
    *
    * <p>{@code getResource} would return only one, and under surefire that one is
    * {@code target/test-classes} — this test's own package shadows the resource package of the same
    * name, and test-classes holds compiled tests rather than any endpoints.json. Asking for all of
    * them and walking each is what reaches the real resources in {@code target/classes}.</p>
    */
   private List<Path> findDatasourceRoots() throws Exception {
      // ClassLoader paths carry no leading slash, unlike Class.getResource.
      Enumeration<URL> urls =
         getClass().getClassLoader().getResources(DATASOURCE_PACKAGE.substring(1));
      List<Path> roots = new ArrayList<>();

      while(urls.hasMoreElements()) {
         URL url = urls.nextElement();

         if("file".equals(url.getProtocol())) {
            roots.add(Paths.get(url.toURI()));
         }
      }

      assertFalse(roots.isEmpty(), "resource package not on the classpath: " + DATASOURCE_PACKAGE);
      return roots;
   }

   private List<Path> findEndpointFiles(List<Path> roots) throws Exception {
      List<Path> files = new ArrayList<>();

      for(Path root : roots) {
         try(Stream<Path> paths = Files.walk(root, 2)) {
            paths.filter(p -> "endpoints.json".equals(p.getFileName().toString()))
               .sorted()
               .forEach(files::add);
         }
      }

      return files;
   }

   /**
    * The {@code *Endpoints} class sitting beside the file, found by scanning rather than by
    * deriving a name from the folder: the two do not line up reliably
    * ({@code adobeanalytics} to {@code AdobeAnalyticsEndpoints}), and a name-mangling rule would
    * fail as a missing class, which reads like a real defect.
    */
   private Class<?> findEndpointsClass(Path dir) throws Exception {
      String pkg = DATASOURCE_PACKAGE.substring(1).replace('/', '.')
         + "." + dir.getFileName();

      try(Stream<Path> paths = Files.list(dir)) {
         List<String> names = paths
            .map(p -> p.getFileName().toString())
            .filter(n -> n.endsWith("Endpoints.class"))
            .map(n -> n.substring(0, n.length() - ".class".length()))
            .toList();

         for(String name : names) {
            Class<?> c = Class.forName(pkg + "." + name);

            if(EndpointJsonQuery.Endpoints.class.isAssignableFrom(c)) {
               return c;
            }
         }
      }

      return null;
   }

   private static final String DATASOURCE_PACKAGE = "/inetsoft/uql/rest/datasource";
}
