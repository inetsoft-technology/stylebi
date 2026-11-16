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
 * The cross-tier guard of the {@code vsHyperlinkExpression} kind (Bug #80459): every
 * {@code (name, assemblyType)} pair in the browser's checked-in
 * {@code hyperlink-pairing-names.json} must be accepted by {@link HyperlinkRegionName}, and the
 * browser's {@code hyperlinkRegionName} output is pinned to that manifest by its own spec. A typo
 * on either side would otherwise surface only as a mint error behind a button the user clicked.
 *
 * <p>Read in place, found the way {@link DynamicValueNamesManifestTest} finds its manifest. A
 * manifest that cannot be found FAILS the test.
 */
@Tag("core")
class HyperlinkNamesManifestTest {
   private static final String APP = "web/projects/portal/src/app";
   private static final String MANIFEST = APP + "/vsobjects/dialog/graph/hyperlink-pairing-names.json";

   private static Path repoRoot() throws Exception {
      String explicit = System.getProperty("stylebi.repo.root");

      if(explicit != null && !explicit.isBlank()) {
         return Path.of(explicit);
      }

      List<Path> starts = new ArrayList<>();
      starts.add(Path.of("").toAbsolutePath());
      starts.add(Path.of(HyperlinkNamesManifestTest.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI()).toAbsolutePath());

      for(Path start : starts) {
         for(Path dir = start; dir != null; dir = dir.getParent()) {
            if(Files.isRegularFile(dir.resolve(MANIFEST))) {
               return dir;
            }
         }
      }

      return fail("hyperlink-pairing-names.json was not found above " + starts + " (looked for " +
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

   /** What the server would say about one manifest entry; empty when it accepts all of it. */
   static List<String> problemsOf(String name, List<String> assemblyTypes) {
      List<String> problems = new ArrayList<>();
      HyperlinkRegionName parsed = HyperlinkRegionName.parse(name);

      if(parsed == null) {
         problems.add(name + ": " + HyperlinkRegionName.problem(name));
         return problems;
      }

      if(!name.equals(parsed.wire())) {
         problems.add(name + ": does not round trip, parsed back as " + parsed.wire());
      }

      if(assemblyTypes.isEmpty()) {
         problems.add(name + ": no assembly type listed");
      }

      Set<String> served = HyperlinkRegionName.typesOf(parsed.form());

      for(String type : assemblyTypes) {
         if(!served.contains(type)) {
            problems.add(name + ": not available on '" + type + "' (server types " + served + ")");
         }
      }

      if(!assemblyTypes.containsAll(served)) {
         problems.add(name + ": lists " + assemblyTypes + " but the server serves " + served);
      }

      return problems;
   }

   @Test
   void everyManifestEntryIsAcceptedByTheServerGrammarAndVocabulary() throws Exception {
      JsonNode manifest = manifest();
      assertTrue(manifest.isArray() && manifest.size() > 0, "manifest must be a non-empty array");

      List<String> problems = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for(JsonNode entry : manifest) {
         String name = entry.path("name").asText("");
         List<String> types = new ArrayList<>();
         entry.path("assemblyTypes").forEach(t -> types.add(t.asText()));

         if(!seen.add(name)) {
            problems.add(name + ": listed twice");
         }

         problems.addAll(problemsOf(name, types));
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void theManifestCoversEveryFormTheServerAccepts() throws Exception {
      Set<String> forms = new HashSet<>();

      for(JsonNode entry : manifest()) {
         HyperlinkRegionName parsed = HyperlinkRegionName.parse(entry.path("name").asText(""));

         if(parsed != null) {
            forms.add(parsed.form());
         }
      }

      assertEquals(Set.of("link", "title", "emptyPlot", "cell", "field", "axis", "text"), forms);
   }

   @Test
   void theCheckRejectsAWrongTypeAndAMalformedNameAndAnIncompleteTypeList() {
      assertFalse(problemsOf("cell[1,2]", List.of("table", "crosstab", "calctable", "chart"))
                     .isEmpty());
      assertFalse(problemsOf("cell[1]", List.of("table")).isEmpty());
      assertFalse(problemsOf("title", List.of()).isEmpty());
      assertFalse(problemsOf("link", List.of("text")).isEmpty());
      assertTrue(problemsOf("link", List.of("text", "image", "gauge")).isEmpty());
   }
}
