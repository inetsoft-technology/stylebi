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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80446: the dialect capability that says whether an ascending ORDER BY returns NULL last.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperNullsSortHighTest {
   @Test
   void db2OraclePostgresqlSortNullHigh() {
      assertTrue(SQLHelper.getSQLHelper("db2").isNullsSortHigh());
      assertTrue(SQLHelper.getSQLHelper("oracle").isNullsSortHigh());
      assertTrue(SQLHelper.getSQLHelper("postgresql").isNullsSortHigh());
   }

   @Test
   void otherDialectsKeepNullLowest() {
      assertFalse(SQLHelper.getSQLHelper("mysql").isNullsSortHigh());
      assertFalse(SQLHelper.getSQLHelper("sql server").isNullsSortHigh());
      assertFalse(SQLHelper.getSQLHelper("h2").isNullsSortHigh());
      assertFalse(new SQLHelper().isNullsSortHigh());
   }
}
