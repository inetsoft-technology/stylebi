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
package inetsoft.mv.trans;

import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82492: a selection bound to a column that its table doesn't have must not be registered
 * as a selection column of the table, or the selections on the table can't be moved up to the
 * MV and no association MV is created for it. A column found the way the selection query finds
 * it (by the bound attribute name or caption) and a calculated field of the viewsheet are still
 * registered.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TransformationDescriptorMissingSelectionColumnTest {
   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, TABLE);
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { REGION, STATE, AMOUNT }, { "East", "NJ", 1 }, { "West", "CA", 2 } }));
      ws.addAssembly(table);

      // what Viewsheet.update() does with the worksheet it loads
      vs = new Viewsheet();
      vs.getVSAssemblyInfo().setName("vs1");
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);
   }

   @Test
   void selectionOnMissingColumnIsNotRegistered() {
      addList("L_region", ref(REGION));
      addList("L_state", ref(STATE));
      addList("L_miss", ref("CUSTOMER_ID"));

      for(int mode : new int[] { TransformationDescriptor.ANALYTIC_MODE,
                                 TransformationDescriptor.RUN_MODE })
      {
         TransformationDescriptor desc = new TransformationDescriptor(vs, mode);

         assertEquals(Set.of(REGION, STATE), names(desc.getSelectionColumns(TABLE, false)));
         assertEquals(Set.of(REGION, STATE),
                      names(desc.getSelectionColumns(Assembly.SELECTION + TABLE, false)));
         assertEquals(Set.of(REGION, STATE), names(desc.getAssociatedSelectionColumns(TABLE)));
      }
   }

   @Test
   void tableWithOnlyAMissingColumnSelectionHasNoSelection() {
      addList("L_miss", ref("CUSTOMER_ID"));
      TransformationDescriptor desc =
         new TransformationDescriptor(vs, TransformationDescriptor.RUN_MODE);

      assertTrue(desc.getSelectionColumns(TABLE, false).isEmpty());
      assertTrue(desc.getAssociatedSelectionColumns(TABLE).isEmpty());
   }

   @Test
   void columnFoundByBoundAttributeIsStillRegistered() {
      // the viewsheet name (an alias) is not in the table, the bound attribute is: registered
      // under the ref's name as before
      ColumnRef alias = ref(REGION);
      alias.setAlias("RegionAlias");
      addList("L_alias", alias);
      TransformationDescriptor desc =
         new TransformationDescriptor(vs, TransformationDescriptor.RUN_MODE);

      assertEquals(Set.of("RegionAlias"), names(desc.getSelectionColumns(TABLE, false)));
   }

   @Test
   void calcFieldSelectionIsStillRegistered() {
      ExpressionRef exp = new ExpressionRef(null, "calc1");
      exp.setExpression("field['" + AMOUNT + "'] * 2");
      CalculateRef calc = new CalculateRef(true);
      calc.setDataRef(exp);
      vs.addCalcField(TABLE, calc);
      addList("L_calc", (CalculateRef) calc.clone());
      addList("L_miss", ref("CUSTOMER_ID"));
      TransformationDescriptor desc =
         new TransformationDescriptor(vs, TransformationDescriptor.RUN_MODE);

      // the calc field and the column it uses
      assertEquals(Set.of("calc1", AMOUNT), names(desc.getAssociatedSelectionColumns(TABLE)));
   }

   private void addList(String name, ColumnRef ref) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      info.setTableName(TABLE);
      info.setDataRef(ref);
      vs.addAssembly(list);
   }

   private static Set<String> names(List<WSColumn> columns) {
      Set<String> names = new HashSet<>();

      for(WSColumn column : columns) {
         names.add(column.getDataRef().getName());
      }

      return names;
   }

   private static ColumnRef ref(String name) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(XSchema.STRING);
      return column;
   }

   private static final String TABLE = "Query1";
   private static final String REGION = "Region";
   private static final String STATE = "State";
   private static final String AMOUNT = "Amount";

   private Viewsheet vs;
}
