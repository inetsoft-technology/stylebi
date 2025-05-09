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

package inetsoft.report.internal;

import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.*;
import inetsoft.uql.text.TextOutput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@SreeHome
public class XNodeMetaTableTest {
   @Test
   public void testSerialize() throws Exception {
      XTypeNode root = new XTypeNode("table");
      List<XTypeNode> cols = new ArrayList<>();
      cols.add(new BooleanType("bool"));
      cols.add(new ByteType("byte"));
      cols.add(new CharacterType("char"));
      cols.add(new DateBaseType("dateBase"));
      cols.add(new DateType("date"));
      cols.add(new DoubleType("double"));
      cols.add(new EnumType("enum"));
      cols.add(new FloatType("float"));
      cols.add(new IntegerType("integer"));
      cols.add(new LongType("long"));
      cols.add(new NumberBaseType("numberBase"));
      cols.add(new RoleType());
      cols.add(new ShortType("short"));
      cols.add(new StringType("string"));
      cols.add(new TextOutput());
      cols.add(new TimeInstantType("timeInstant"));
      cols.add(new TimeType("time"));
      cols.add(new UserDefinedType("userDefined"));
      cols.add(new UserType());
      cols.add(new XTypeNode("xtype"));

      for(XTypeNode col : cols) {
         root.addChild(col);
      }

      XNodeMetaTable originalTable = new XNodeMetaTable(root);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeMetaTable.class, deserializedTable.getClass());
   }
}
