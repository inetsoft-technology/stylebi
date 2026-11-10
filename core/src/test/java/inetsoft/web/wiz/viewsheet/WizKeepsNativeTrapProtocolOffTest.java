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
package inetsoft.web.wiz.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80476 guard. With {@code confirmed=false, checkTrap=true}, {@code
 * VSAssemblyInfoHandler.apply} reverts the write and sends an INFO {@code VSTrapCommand}; the
 * capturing dispatcher does not turn that into a warning, so the write would silently do nothing.
 * The trap warnings added for this bug are warn-only and rely on every wiz path keeping the
 * native check off. This scans the wiz sources: every {@code apply(...)} call on an info handler
 * must pass a literal {@code false} as its checkTrap argument (the fifth), and nothing may call
 * {@code setCheckTrap(true)}.
 */
@Tag("core")
class WizKeepsNativeTrapProtocolOffTest {
   private static final Path WIZ = Path.of("src", "main", "java", "inetsoft", "web", "wiz");

   private static final Pattern APPLY =
      Pattern.compile("(?:assemblyInfoHandler|infoHandler|infoHander|vsAssemblyInfoHandler)" +
                      "\\s*\\.\\s*apply\\s*\\(");

   @Test
   void noWizServicePassesCheckTrapTrue() throws IOException {
      Path wiz = locate();
      List<String> offenders = new ArrayList<>();
      int applyCalls = 0;

      try(Stream<Path> files = Files.walk(wiz)) {
         for(Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
            String source = Files.readString(file);

            if(source.contains("setCheckTrap(true)")) {
               offenders.add(file + ": setCheckTrap(true)");
            }

            Matcher matcher = APPLY.matcher(source);

            while(matcher.find()) {
               List<String> args = arguments(source, matcher.end());

               if(args.size() >= 8) {
                  applyCalls++;

                  if(!"false".equals(args.get(4))) {
                     offenders.add(file + ": apply(...) checkTrap argument is '" + args.get(4) +
                                   "'");
                  }
               }
            }
         }
      }

      assertTrue(offenders.isEmpty(), offenders.toString());
      assertTrue(applyCalls > 0, "the scan matched no apply(...) call; the pattern is stale");
   }

   /** The wiz source directory, found by walking up from the test's working directory. */
   private static Path locate() {
      for(Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
         for(Path candidate : new Path[]{ dir.resolve(WIZ), dir.resolve("core").resolve(WIZ) }) {
            if(Files.isDirectory(candidate)) {
               return candidate;
            }
         }
      }

      return fail("the wiz source directory was not found above " + Path.of("").toAbsolutePath());
   }

   /** The top-level, whitespace-trimmed arguments of the call whose '(' ends at {@code from}. */
   private static List<String> arguments(String source, int from) {
      List<String> args = new ArrayList<>();
      int depth = 1;
      int start = from;

      for(int i = from; i < source.length() && depth > 0; i++) {
         char c = source.charAt(i);

         if(c == '(' || c == '{' || c == '[') {
            depth++;
         }
         else if(c == ')' || c == '}' || c == ']') {
            depth--;

            if(depth == 0) {
               args.add(source.substring(start, i).trim());
            }
         }
         else if(c == ',' && depth == 1) {
            args.add(source.substring(start, i).trim());
            start = i + 1;
         }
      }

      return args;
   }
}
