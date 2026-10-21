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
package inetsoft.test.dc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares generated text with a reviewed snapshot kept under {@code src/test/resources}.
 *
 * <p>A mismatch fails with the differing lines. Run with {@code -Ddc.snapshot.update=true} to
 * rewrite the snapshot instead, then review the change with {@code git diff}. A missing
 * snapshot fails too, so CI never creates one by itself.</p>
 */
public final class DcSnapshot {
   public static final String UPDATE_PROPERTY = "dc.snapshot.update";
   private static final int MAX_DIFF_LINES = 30;

   private DcSnapshot() {
   }

   /**
    * @param resource the snapshot path relative to {@code src/test/resources}.
    * @param actual   the generated lines.
    */
   public static void verify(String resource, List<String> actual) {
      String text = String.join("\n", actual) + "\n";

      if(Boolean.getBoolean(UPDATE_PROPERTY)) {
         write(resource, text);
         return;
      }

      List<String> expected = read(resource);

      if(expected == null) {
         fail("Missing snapshot " + resource + "; run with -D" + UPDATE_PROPERTY +
              "=true and review the new file");
      }

      List<String> diff = diff(expected, actual);

      if(!diff.isEmpty()) {
         fail("Snapshot " + resource + " differs, run with -D" + UPDATE_PROPERTY +
              "=true to accept (in the surefire time zone, America/New_York):\n" +
              String.join("\n", diff));
      }
   }

   private static List<String> read(String resource) {
      try(InputStream in = DcSnapshot.class.getClassLoader().getResourceAsStream(resource)) {
         if(in == null) {
            return null;
         }

         String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
         List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));

         if(!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
         }

         return lines;
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private static void write(String resource, String text) {
      try {
         // surefire runs in a work directory, so go by the module directory it reports
         Path module = Path.of(System.getProperty("basedir", "."));
         Path path = module.resolve(Path.of("src", "test", "resources", resource));
         Files.createDirectories(path.getParent());
         Files.writeString(path, text, StandardCharsets.UTF_8);
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   /** The first differing line and the number of differing lines. */
   private static List<String> diff(List<String> expected, List<String> actual) {
      List<String> diff = new ArrayList<>();
      int n = Math.max(expected.size(), actual.size());
      int count = 0;

      for(int i = 0; i < n; i++) {
         String e = i < expected.size() ? expected.get(i) : null;
         String a = i < actual.size() ? actual.get(i) : null;

         if(e == null ? a != null : !e.equals(a)) {
            if(count++ < MAX_DIFF_LINES) {
               diff.add("line " + (i + 1) + "\n  expected: " + e + "\n  actual:   " + a);
            }
         }
      }

      if(count > MAX_DIFF_LINES) {
         diff.add("... " + (count - MAX_DIFF_LINES) + " more differing lines (" + expected.size() +
                  " expected, " + actual.size() + " actual lines; a line inserted or removed " +
                  "shifts everything after it)");
      }

      return diff;
   }
}
