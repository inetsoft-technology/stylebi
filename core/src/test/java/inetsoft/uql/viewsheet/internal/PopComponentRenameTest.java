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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79770: renaming the assembly a Text/Image uses as its pop component must
 * carry the pop component reference along.
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
class PopComponentRenameTest {
   @Test
   void textPopComponentFollowsRename() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setPopComponentValue("RevenueGauge");

      info.renameDepended("RevenueGauge", "RevenueGauge2026", null);

      assertEquals("RevenueGauge2026", info.getPopComponentValue());
   }

   @Test
   void imagePopComponentFollowsRename() {
      ImageVSAssemblyInfo info = new ImageVSAssemblyInfo();
      info.setPopComponentValue("Chart1");

      info.renameDepended("Chart1", "Chart2", null);

      assertEquals("Chart2", info.getPopComponentValue());
   }

   @Test
   void unrelatedRenameLeavesPopComponent() {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.setPopComponentValue("RevenueGauge");

      info.renameDepended("Other", "Other2", null);

      assertEquals("RevenueGauge", info.getPopComponentValue());
   }
}
