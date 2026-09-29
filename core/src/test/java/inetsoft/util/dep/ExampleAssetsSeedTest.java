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
package inetsoft.util.dep;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the viewsheets, worksheets and manifest of the example bundle that a new server
 * imports ({@code community-examples/examples.zip}) against {@link ImportedAssetProperties}.
 * The enterprise fuzzer starts from the same entries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ExampleAssetsSeedTest {
   @ParameterizedTest(name = "{0}")
   @MethodSource("entries")
   void exampleSurvivesSaveAndReload(String name, byte[] content) throws Exception {
      assertTrue(ImportedAssetProperties.check(content, true), name + " was not checked");
   }

   static Stream<Arguments> entries() throws Exception {
      // core/target/test-classes -> community/community-examples/examples.zip
      Path classes = Path.of(ExampleAssetsSeedTest.class.getResource("/").toURI());
      Path zip = classes.resolve("../../../community-examples/examples.zip").normalize();
      List<Arguments> entries = new ArrayList<>();

      try(ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
         for(ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
            String name = entry.getName();

            if(name.startsWith("VIEWSHEET_") || name.startsWith("WORKSHEET_") ||
               name.equals("JarFileInfo.xml"))
            {
               entries.add(Arguments.of(name, in.readAllBytes()));
            }
         }
      }

      entries.sort(Comparator.comparing(a -> (String) a.get()[0]));
      return entries.stream();
   }
}
