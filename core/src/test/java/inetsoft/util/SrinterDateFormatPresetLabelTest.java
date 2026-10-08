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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78070: the Format pane date presets LONG and SHORT were labelled with the data-type
 * keys {@code Long} and {@code Short}. Feature #58749 changed those values to
 * "Long - Integer(>2B)" and "Short - Integer(<32K)", so the presets showed the data-type text.
 * <p>
 * This test reads the label keys that {@code formatting-pane.component.ts} actually uses for
 * the LONG and SHORT presets and checks that every srinter bundle resolves them (with the
 * base bundle as fallback, like {@code Catalog}) to the same text as the bundle's own
 * {@code Full}/{@code Medium} style labels, i.e. not to a data-type label.
 */
@Tag("core")
class SrinterDateFormatPresetLabelTest {
   private static final String BASE = "src/main/resources/inetsoft/util/srinter.properties";
   private static final String PANE =
      "../web/projects/portal/src/app/format/objects/formatting-pane.component.ts";
   private static final Pattern PRESET = Pattern.compile(
      "\\{value:\\s*\"(LONG|SHORT)\",\\s*label:\\s*\"_#\\(js:([^)]+)\\)\"}");

   static Stream<String> bundlePaths() {
      return Stream.of(
         BASE,
         "../community-examples/localize/srinter_en_US.properties",
         "../community-examples/localize/srinter_fr_FR.properties",
         "../community-examples/localize/srinter_ja_JP.properties",
         "../community-examples/localize/srinter_zh_CN.properties"
      );
   }

   @ParameterizedTest
   @MethodSource("bundlePaths")
   void dateFormatPresetLabelsAreNotDataTypeLabels(String bundlePath) throws Exception {
      Map<String, String> keys = presetKeys();
      PropertyResourceBundle base = load(resolve(BASE));
      PropertyResourceBundle bundle = load(resolve(bundlePath));

      for(Map.Entry<String, String> e : keys.entrySet()) {
         String key = e.getValue();
         assertTrue(base.containsKey(key), "Base srinter bundle has no key " + key);
         String label = bundle.containsKey(key) ? bundle.getString(key) : base.getString(key);

         // the data-type keys carry the type details, e.g. "Long - Integer(>2B)"
         for(String typeKey : List.of("Long", "Short")) {
            if(bundle.containsKey(typeKey)) {
               assertNotEquals(bundle.getString(typeKey), label,
                  e.getKey() + " preset label in " + bundlePath + " is the data-type label");
            }
         }

         assertFalse(label.contains("("),
            e.getKey() + " preset label in " + bundlePath + " has type details: " + label);
      }

      if(BASE.equals(bundlePath)) {
         assertEquals("Long", bundle.getString(keys.get("LONG")));
         assertEquals("Short", bundle.getString(keys.get("SHORT")));
      }
   }

   private static Map<String, String> presetKeys() throws Exception {
      Path pane = resolve(PANE);
      assertTrue(Files.isRegularFile(pane), "Format pane source not found: " + pane);
      Matcher matcher = PRESET.matcher(Files.readString(pane));
      Map<String, String> keys = new HashMap<>();

      while(matcher.find()) {
         keys.put(matcher.group(1), matcher.group(2));
      }

      assertEquals(Set.of("LONG", "SHORT"), keys.keySet(),
         "LONG/SHORT date presets not found in " + pane);
      return keys;
   }

   private static PropertyResourceBundle load(Path file) throws Exception {
      assertTrue(Files.isRegularFile(file), "Catalog bundle not found: " + file);

      try(InputStream in = Files.newInputStream(file)) {
         return new PropertyResourceBundle(in);
      }
   }

   private static Path resolve(String relativePath) {
      String basedir = System.getProperty("basedir");
      assertNotNull(basedir, "basedir system property is not set (it is set by Maven surefire)");
      return Path.of(basedir).resolve(relativePath).normalize();
   }
}
