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
package inetsoft.web.wiz.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Charter C7, no Site Administrator requirement anywhere: a scan of every main source file the
 * data modeling run created. After comments and imports are removed, no file may mention an admin
 * (the admin caller guard, {@code isSiteAdmin}, an administrator resource type or role, an EM
 * component grant) or declare a {@code @Secured} / {@code @RequiredPermission} data gate. The
 * authorization of these routes is the per-resource permission checks of the Portal services,
 * asserted route by route in {@code WizDataModelGatesRouteTableTest}.
 *
 * <p>A scan can only look for the spellings it knows, so the pattern is deliberately broad: any
 * identifier or string containing {@code admin} (case-insensitive) or {@code EM_COMPONENT} or
 * {@code SYSTEM} resource names fails it. The package name of the Portal's folder service
 * ({@code inetsoft.web.admin...}) appears only in import lines, which are removed first.</p>
 */
@Tag("core")
class NoAdminGateTest {
   private static final Pattern FORBIDDEN = Pattern.compile(
      "(?i)admin|EM_COMPONENT|ResourceType\\.SYSTEM|@Secured|@RequiredPermission|" +
      "isSiteAdmin|isSysAdmin|isOrgAdmin|hasRole\\(");

   /** The files this run created (lane J1.0 .. J4), relative to the module's main source root. */
   private static final List<String> PATTERNS = List.of(
      "inetsoft/web/wiz/controller/WizDataModel*.java",
      "inetsoft/web/wiz/controller/WizLogicalModel*.java",
      "inetsoft/web/wiz/controller/WizVpm*.java",
      "inetsoft/web/wiz/controller/WizPhysicalView*.java",
      "inetsoft/web/wiz/controller/WizModelKindOps.java",
      "inetsoft/web/portal/controller/database/VpmService.java");

   @Test
   void noFileOfTheRunMentionsAnAdminGate() throws IOException {
      Path root = mainRoot();
      List<Path> files = filesOfTheRun(root);

      assertTrue(files.size() >= 20, "the scan found only " + files.size() + " files under " + root);
      List<String> hits = new ArrayList<>();

      for(Path file : files) {
         for(String hit : scan(Files.readString(file))) {
            hits.add(root.relativize(file) + ": " + hit);
         }
      }

      assertTrue(hits.isEmpty(), "an admin gate or admin reference was added: " + hits);
   }

   @Test
   void theScanSeesWhatItIsMeantToSee() {
      assertFalse(scan("if(!AdminAiCallerGuard.isSiteAdmin(principal)) { }").isEmpty());
      assertFalse(scan("boolean a = securityEngine.isSiteAdmin(p);").isEmpty());
      assertFalse(scan("@Secured(@RequiredPermission(resourceType = ResourceType.EM_COMPONENT))")
                     .isEmpty());
      assertFalse(scan("String s = \"Admin only\";").isEmpty());
      // comments and imports are not code
      assertTrue(scan("// never an administrator gate\n/* admin */\n" +
                      "import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;\n" +
                      "class A { }").isEmpty());
   }

   private static List<String> scan(String source) {
      String code = source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "")
         .replaceAll("(?m)^\\s*import .*$", "");
      List<String> hits = new ArrayList<>();
      Matcher matcher = FORBIDDEN.matcher(code);

      while(matcher.find()) {
         int from = Math.max(0, matcher.start() - 30);
         int to = Math.min(code.length(), matcher.end() + 30);
         hits.add(code.substring(from, to).replace('\n', ' ').strip());
      }

      return hits;
   }

   private static List<Path> filesOfTheRun(Path root) throws IOException {
      List<Path> files = new ArrayList<>();

      for(String pattern : PATTERNS) {
         Path dir = root.resolve(pattern.substring(0, pattern.lastIndexOf('/')));
         PathMatcher matcher = FileSystems.getDefault().getPathMatcher(
            "glob:" + pattern.substring(pattern.lastIndexOf('/') + 1));

         try(Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> matcher.matches(p.getFileName())).forEach(files::add);
         }
      }

      return files;
   }

   private static Path mainRoot() {
      // surefire runs in core/target/test-workdir-N: walk up to the module that holds the sources
      for(Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath(); dir != null;
          dir = dir.getParent())
      {
         for(String candidate : new String[] { "src/main/java", "core/src/main/java" }) {
            Path path = dir.resolve(candidate);

            if(Files.isDirectory(path.resolve("inetsoft/web/wiz/controller"))) {
               return path;
            }
         }
      }

      throw new AssertionError("main source root not found from " + System.getProperty("user.dir"));
   }
}
