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
package inetsoft.web.service;

import inetsoft.test.*;
import inetsoft.util.Catalog;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #78070: runs the real {@link LocalizationService#localize(Reader, Writer, Catalog)}
 * substitution on the real portal sources, as the server does when it builds the localized
 * web resources. The Format pane LONG/SHORT date presets must come out as plain
 * "Long"/"Short", while the data-type lists that use the {@code Long}/{@code Short} keys keep
 * the data-type text from Feature #58749.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormattingPaneDatePresetLocalizeTest {
   private static final String PORTAL = "../web/projects/portal/src/app/";

   @Test
   void formattingPaneDatePresetsLocalizeToPlainLabels() throws IOException {
      String out = localize(PORTAL + "format/objects/formatting-pane.component.ts");

      assertTrue(out.contains("{value: \"LONG\", label: \"Long\"}"), out);
      assertTrue(out.contains("{value: \"SHORT\", label: \"Short\"}"), out);
      assertFalse(out.contains("Integer("), out);
   }

   @Test
   void dataTypeListKeepsDataTypeLabels() throws IOException {
      String out = localize(PORTAL + "common/data/xschema.ts");

      assertTrue(out.contains("{ label: \"Long - Integer(>2B)\", data: XSchema.LONG }"), out);
      assertTrue(out.contains("{ label: \"Short - Integer(<32K)\", data: XSchema.SHORT }"), out);
   }

   private static String localize(String path) throws IOException {
      LocalizationService service = new LocalizationService(mock(FileSystemService.class));
      Catalog catalog = Catalog.getResCatalog(
         Catalog.DEFAULT_RESOURCE, Catalog.DEFAULT_RESOURCE, Locale.US);
      StringWriter writer = new StringWriter();

      try(Reader reader = Files.newBufferedReader(resolve(path), StandardCharsets.UTF_8)) {
         service.localize(reader, writer, catalog);
      }

      return writer.toString();
   }

   // @SreeHome changes the working directory, so resolve against the module directory.
   private static Path resolve(String relativePath) {
      String basedir = System.getProperty("basedir");
      assertNotNull(basedir, "basedir system property is not set (it is set by Maven surefire)");
      return Path.of(basedir).resolve(relativePath).normalize();
   }
}
