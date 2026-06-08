/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
 * Unit tests for {@link DerbyHelper}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DerbyHelperTest {
   // Bug #75322. Derby only allows 'select *' in EXISTS and NOT EXISTS
   // subqueries (error 42X38), so the maxrows clause, which wraps the query
   // in 'select * from (...) fetch first n rows only', must not be applied
   // to a where subquery (e.g. IN / NOT IN).
   @Test
   void maxRowsNotSupportedInWhereSubquery() {
      DerbyHelper helper = new DerbyHelper();
      assertFalse(helper.supportsOperation(SQLHelper.MAXROWS, SQLHelper.WHERE_SUBQUERY));
   }

   // the maxrows clause is still supported outside of where subqueries.
   @Test
   void maxRowsSupportedOutsideWhereSubquery() {
      DerbyHelper helper = new DerbyHelper();
      assertTrue(helper.supportsOperation(SQLHelper.MAXROWS, null));
      assertTrue(helper.supportsOperation(SQLHelper.MAXROWS));
   }
}
