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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cross-tier guard (Bug #80459, design 7.4): every {@code (name, assemblyType)} pair the
 * browser's checked-in manifest says a host uses must be accepted by the server's grammar and
 * vocabulary. A literal typo in one of the host templates would otherwise only show up as a mint
 * error on a button a user clicked.
 *
 * <p>The manifest is read in place, from the web tree next to {@code core}: the working directory
 * of a Maven test run is {@code core/target/test-workdir-N}, so the repository root is found by
 * climbing from the working directory and from the test classes until
 * {@code web/projects/portal/src/app} turns up. The system property {@code stylebi.repo.root}
 * names it explicitly. A manifest that cannot be found FAILS the test: a guard that silently
 * skips when its input moves guards nothing.
 */
@Tag("core")
class DynamicValueNamesManifestTest {
   private static final String APP = "web/projects/portal/src/app";
   private static final String MANIFEST = APP + "/widget/dynamic-combo-box/dynamic-value-names.json";

   private static Path repoRoot() throws Exception {
      String explicit = System.getProperty("stylebi.repo.root");

      if(explicit != null && !explicit.isBlank()) {
         return Path.of(explicit);
      }

      List<Path> starts = new ArrayList<>();
      starts.add(Path.of("").toAbsolutePath());
      starts.add(Path.of(DynamicValueNamesManifestTest.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).toAbsolutePath());

      for(Path start : starts) {
         for(Path dir = start; dir != null; dir = dir.getParent()) {
            if(Files.isRegularFile(dir.resolve(MANIFEST))) {
               return dir;
            }
         }
      }

      return fail("dynamic-value-names.json was not found above " + starts + " (looked for " +
                  MANIFEST + "). Pass -Dstylebi.repo.root=<repository root>.");
   }

   private static JsonNode manifest() throws Exception {
      Path file = repoRoot().resolve(MANIFEST);

      try {
         return new ObjectMapper().readTree(Files.readString(file));
      }
      catch(IOException ex) {
         return fail("cannot read " + file + ": " + ex.getMessage());
      }
   }

   /** The placeholders a computed host literal may carry, replaced by one concrete value. */
   static String concrete(String name) {
      String s = name.replace("<shelf>", "x").replace("<i>", "0");

      assertFalse(s.contains("<") || s.contains(">"),
                  "unsupported placeholder in manifest name '" + name + "'");
      return s;
   }

   /** What the server would say about one manifest entry; empty when it accepts all of it. */
   static List<String> problemsOf(String name, List<String> assemblyTypes) {
      List<String> problems = new ArrayList<>();
      String concrete = concrete(name);
      DynamicValueName parsed = DynamicValueName.parse(concrete);

      if(parsed == null) {
         problems.add(name + ": " + DynamicValueName.problem(concrete));
         return problems;
      }

      if(assemblyTypes.isEmpty()) {
         problems.add(name + ": no assembly type listed");
      }

      for(String type : assemblyTypes) {
         String problem = DynamicValueVocabulary.problem(type, parsed);

         if(problem != null) {
            problems.add(name + " on " + type + ": " + problem);
         }
      }

      return problems;
   }

   @Test
   void theServerAcceptsEveryNameAndTypeTheManifestDeclares() throws Exception {
      JsonNode entries = manifest();

      assertTrue(entries.isArray() && entries.size() > 0, "the manifest is empty or not an array");

      List<String> problems = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for(JsonNode entry : entries) {
         String name = entry.path("name").asText("");
         List<String> types = new ArrayList<>();

         entry.path("assemblyTypes").forEach(t -> types.add(t.asText()));

         if(!seen.add(name)) {
            problems.add(name + ": declared twice");
         }

         problems.addAll(problemsOf(name, types));
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void everyEntryNamesAHostTemplateThatExists() throws Exception {
      Path app = repoRoot().resolve(APP);
      List<String> missing = new ArrayList<>();

      for(JsonNode entry : manifest()) {
         String host = entry.path("host").asText("");

         if(host.isBlank() || !Files.isRegularFile(app.resolve(host))) {
            missing.add(entry.path("name").asText() + " -> '" + host + "'");
         }
      }

      assertTrue(missing.isEmpty(), "manifest entries without a host template: " + missing);
   }

   /**
    * Positive control: the very check above must flag a typo, a name valid on other types only, a
    * malformed name and an entry with no type. A guard that cannot fail proves nothing.
    */
   @Test
   void theCheckFlagsEveryKindOfBrokenEntry() {
      assertEquals(List.of(), problemsOf("visible", List.of("text", "chart")));

      assertFalse(problemsOf("visble", List.of("text")).isEmpty(), "a typo");
      assertFalse(problemsOf("enabled", List.of("text", "oval")).isEmpty(), "enabled on a shape");
      assertEquals(1, problemsOf("enabled", List.of("text", "oval")).size());
      assertFalse(problemsOf("enabled", List.of("groupcontainer")).isEmpty(), "a dead name");
      assertFalse(problemsOf("prop:visible", List.of("text")).isEmpty(), "an explicit prefix");
      assertFalse(problemsOf("visible", List.of()).isEmpty(), "no type");
      assertFalse(problemsOf("visible", List.of("cube")).isEmpty(), "an uncovered type");
      assertFalse(problemsOf("binding:<shelf>[<i>].bogus", List.of("chart")).isEmpty(),
                  "a bad binding field");

      // Placeholders are filled in before the check, so a computed literal is checked too.
      assertEquals(List.of(), problemsOf("binding:<shelf>[<i>].column", List.of("chart")));
      assertThrows(AssertionError.class, () -> concrete("binding:<axis>[<i>].column"));
   }
}
