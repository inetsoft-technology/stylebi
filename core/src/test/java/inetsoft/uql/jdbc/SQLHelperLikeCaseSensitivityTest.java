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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79417. {@link SQLHelper#getLikeCaseSensitivity()} is TRUE only for the products whose
 * default LIKE is known to be case sensitive; everything else keeps the global setting.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SQLHelperLikeCaseSensitivityTest {
   @Test
   void verifiedProductsAreCaseSensitive() {
      assertEquals(Boolean.TRUE, new DB2SQLHelper().getLikeCaseSensitivity());
      assertEquals(Boolean.TRUE, new OracleSQLHelper().getLikeCaseSensitivity());
      assertEquals(Boolean.TRUE, new PostgreSQLHelper().getLikeCaseSensitivity());
      assertEquals(Boolean.TRUE, new DerbyHelper().getLikeCaseSensitivity());
      assertEquals(Boolean.TRUE, new H2Helper().getLikeCaseSensitivity());
   }

   @Test
   void otherProductsAreUnknown() {
      assertNull(new SnowflakeHelper().getLikeCaseSensitivity());
      assertNull(new ExasolHelper().getLikeCaseSensitivity());
      assertNull(new VerticaHelper().getLikeCaseSensitivity());
      // extends DB2SQLHelper but is not verified
      assertNull(new InformixSQLHelper().getLikeCaseSensitivity());
      assertNull(new MySQLHelper().getLikeCaseSensitivity());
      assertNull(new SQLServerHelper().getLikeCaseSensitivity());
      assertNull(new SQLHelper() {
         @Override
         public String getSQLHelperType() {
            return "test";
         }
      }.getLikeCaseSensitivity());
   }
}
