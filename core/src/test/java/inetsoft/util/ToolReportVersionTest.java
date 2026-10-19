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

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Tool#getReportVersion()} must follow the Maven project version recorded in the core
 * jar's manifest. It used to return a hard-coded "1.2.0", so a 2.0.1 server reported itself
 * as 1.2.0 in the startup log, the status dump, server monitoring and the SSO token.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ToolReportVersionTest {
   private static Field field;
   private Object saved;

   @BeforeAll
   static void findField() throws Exception {
      field = Tool.class.getDeclaredField("reportVersion");
      field.setAccessible(true);
   }

   @BeforeEach
   void save() throws Exception {
      saved = field.get(null);
   }

   @AfterEach
   void restore() throws Exception {
      field.set(null, saved);
   }

   @ParameterizedTest
   @CsvSource({ "2.1.0-SNAPSHOT, 2.1.0", "2.0.0, 2.0.0", "2.0.1-rc1, 2.0.1" })
   void versionIsSpecificationVersionWithoutQualifier(String specVersion, String expected)
      throws Exception
   {
      field.set(null, specVersion);
      assertEquals(expected, Tool.getReportVersion());
   }

   @Test
   void missingSpecificationVersionIsDevelopmentBuild() throws Exception {
      field.set(null, null);
      assertEquals(Catalog.getCatalog().getString("Development Build"), Tool.getReportVersion());
   }
}
