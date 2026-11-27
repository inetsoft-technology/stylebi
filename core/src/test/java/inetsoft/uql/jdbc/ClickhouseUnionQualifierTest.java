/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ClickhouseUnionQualifierTest {
   @Test
   void clickhouseUnionIsExplicitlyDistinct() {
      assertEquals("DISTINCT ", new ClickhouseHelper().getUnionQualifier(true));
   }

   @Test
   void clickhouseUnionAllKeepsAll() {
      assertEquals("ALL ", new ClickhouseHelper().getUnionQualifier(false));
   }

   @Test
   void otherDialectsUnchanged() {
      SQLHelper helper = new OracleSQLHelper();
      assertEquals("", helper.getUnionQualifier(true));
      assertEquals("ALL ", helper.getUnionQualifier(false));
   }

   @Test
   void clickhouseIntersectExceptIsExplicitlyDistinct() {
      assertEquals("DISTINCT ", new ClickhouseHelper().getIntersectExceptQualifier(true));
      assertEquals("ALL ", new ClickhouseHelper().getIntersectExceptQualifier(false));
   }

   @Test
   void otherDialectsIntersectExceptUnchanged() {
      SQLHelper helper = new OracleSQLHelper();
      assertEquals("", helper.getIntersectExceptQualifier(true));
      assertEquals("ALL ", helper.getIntersectExceptQualifier(false));
   }
}
