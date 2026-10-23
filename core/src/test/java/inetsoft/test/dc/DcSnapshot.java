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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares generated lines with a reviewed snapshot kept under {@code src/test/resources}.
 *
 * <p>A mismatch fails with the differing lines. Run with {@code -Ddc.snapshot.update=true} to
 * rewrite the snapshot instead (in the surefire time zone, America/New_York), then review the
 * change with {@code git diff}. A missing snapshot fails, so CI never creates one by itself.</p>
 */
public final class DcSnapshot {
   public static final String UPDATE_PROPERTY = "dc.snapshot.update";

   private DcSnapshot() {
   }

   /** @param resource the snapshot path relative to {@code src/test/resources}. */
   public static void verify(String resource, List<String> actual) {
      String text = String.join("\n", actual) + "\n";

      if(Boolean.getBoolean(UPDATE_PROPERTY)) {
         write(resource, text);
         return;
      }

      try(InputStream in = DcSnapshot.class.getClassLoader().getResourceAsStream(resource)) {
         if(in == null) {
            fail("Missing snapshot " + resource + "; run with -D" + UPDATE_PROPERTY +
                 "=true and review the new file");
         }

         // a line-by-line comparison names the first line that differs
         String expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
         assertEquals(expected.split("\n", -1).length, text.split("\n", -1).length,
                      "number of lines in " + resource + " (run with -D" + UPDATE_PROPERTY +
                      "=true to accept)");

         String[] expectedLines = expected.split("\n", -1);
         String[] actualLines = text.split("\n", -1);

         for(int i = 0; i < expectedLines.length; i++) {
            assertEquals(expectedLines[i], actualLines[i],
                         resource + " line " + (i + 1) + " (run with -D" + UPDATE_PROPERTY +
                         "=true to accept)");
         }
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private static void write(String resource, String text) {
      try {
         // surefire runs in a work directory, so go by the module directory it reports
         Path path = Path.of(System.getProperty("basedir", "."))
            .resolve(Path.of("src", "test", "resources", resource));
         Files.createDirectories(path.getParent());
         Files.writeString(path, text, StandardCharsets.UTF_8);
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }
}
