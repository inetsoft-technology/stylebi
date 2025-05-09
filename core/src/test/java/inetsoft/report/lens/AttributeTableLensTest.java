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

package inetsoft.report.lens;

import inetsoft.report.StyleConstants;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;

@SreeHome
public class AttributeTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      AttributeTableLens originalTable = new AttributeTableLens(XTableUtil.getDefaultTableLens());
      originalTable.setAlignment(0, 0, StyleConstants.H_CENTER);
      originalTable.setColBorderColor(1, Color.BLUE);
      originalTable.setColForeground(2, Color.RED);
      originalTable.setColFont(2, new Font("Arial", Font.PLAIN, 12));
      originalTable.setRowHeight(1, 100);
      originalTable.setRowBackground(1, Color.CYAN);
      originalTable.setInsets(1, 1, new Insets(10, 10, 10, 10));
      originalTable.setRowLineWrap(1, true);

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AttributeTableLens.class, deserializedTable.getClass());
   }
}
