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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81696: the property dialog keeps a list input's query binding after the user turns the
 * query off. The binding's data type must only apply while the list reads from the query;
 * otherwise an embedded date list is typed by a stale integer column and its selected values
 * are converted to null.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ListInputStaleBindingDataTypeTest {
   @Autowired
   private IndexedStorage indexedStorage;

   @AfterEach
   void tearDown() {
      indexedStorage.remove(KEY);
   }

   /** The assembly types that extend ListInputVSAssemblyInfo. */
   private static final Function<Viewsheet, ListInputVSAssembly>[] ASSEMBLIES = new Function[] {
      (Function<Viewsheet, ListInputVSAssembly>) vs -> new CheckBoxVSAssembly(vs, "In1"),
      (Function<Viewsheet, ListInputVSAssembly>) vs -> new RadioButtonVSAssembly(vs, "In1"),
      (Function<Viewsheet, ListInputVSAssembly>) vs -> new ComboBoxVSAssembly(vs, "In1")
   };

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 2 })
   void staleBindingIsIgnoredForEmbeddedAndNoneSource(int assemblyIdx) {
      for(int stype : new int[] { ListInputVSAssembly.EMBEDDED_SOURCE,
                                  ListInputVSAssembly.NONE_SOURCE })
      {
         ListInputVSAssemblyInfo info = info(assemblyIdx, stype);
         assertEquals(XSchema.DATE, info.getDataType(), "source type " + stype);
      }
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 2 })
   void bindingTypeStillAppliesWhileTheQueryIsUsed(int assemblyIdx) {
      for(int stype : new int[] { ListInputVSAssembly.BOUND_SOURCE,
                                  ListInputVSAssembly.MERGE_SOURCE })
      {
         ListInputVSAssemblyInfo info = info(assemblyIdx, stype);
         assertEquals(XSchema.INTEGER, info.getDataType(), "source type " + stype);
      }
   }

   @Test
   void checkBoxKeepsEmbeddedDateSelectionAfterQueryIsTurnedOff() {
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) info(0, ListInputVSAssembly.EMBEDDED_SOURCE);
      java.util.Date day = java.sql.Timestamp.valueOf("2026-10-20 00:00:00");

      info.setSelectedObjects(new Object[] { day });

      assertArrayEquals(new Object[] { day }, info.getSelectedObjects());
      assertEquals(XSchema.DATE, info.getDataType());
   }

   @Test
   void selectedDateSurvivesXmlRoundTripWithStaleBinding() throws Exception {
      Viewsheet vs = new Viewsheet();
      CheckBoxVSAssembly box = new CheckBoxVSAssembly(vs, "In1");
      CheckBoxVSAssemblyInfo info = (CheckBoxVSAssemblyInfo) box.getVSAssemblyInfo();
      configure(info, ListInputVSAssembly.EMBEDDED_SOURCE);
      java.util.Date day = java.sql.Timestamp.valueOf("2026-10-20 00:00:00");
      info.setSelectedObjects(new Object[] { day });
      vs.addAssembly(box);

      indexedStorage.putXMLSerializable(KEY, vs);
      Viewsheet reopened = (Viewsheet) indexedStorage.getXMLSerializable(KEY, null);

      CheckBoxVSAssemblyInfo reloaded =
         (CheckBoxVSAssemblyInfo) reopened.getAssembly("In1").getVSAssemblyInfo();
      assertEquals(1, reloaded.getSelectedObjects().length);
      assertEquals(day.getTime(), ((java.util.Date) reloaded.getSelectedObjects()[0]).getTime());
      assertEquals(XSchema.DATE, reloaded.getDataType());
   }

   private static ListInputVSAssemblyInfo info(int assemblyIdx, int stype) {
      ListInputVSAssembly assembly = ASSEMBLIES[assemblyIdx].apply(new Viewsheet());
      ListInputVSAssemblyInfo info = (ListInputVSAssemblyInfo) assembly.getVSAssemblyInfo();
      configure(info, stype);
      return info;
   }

   /** An embedded date list plus a stored query binding on an integer column. */
   private static void configure(ListInputVSAssemblyInfo info, int stype) {
      ListData data = new ListData();
      data.setDataType(XSchema.DATE);
      data.setValues(new Object[] { java.sql.Timestamp.valueOf("2026-10-20 00:00:00"),
                                    java.sql.Timestamp.valueOf("2026-10-10 00:00:00") });
      data.setLabels(new String[] { "2026-10-20", "2026-10-10" });
      info.setListData(data);
      // the property dialog stores the embedded list's type in the assembly's own data type
      info.setDataType(XSchema.DATE);

      AttributeRef attr = new AttributeRef(null, "ORDER_ID");
      attr.setDataType(XSchema.INTEGER);
      ColumnRef column = new ColumnRef(attr);
      ListBindingInfo binding = new ListBindingInfo();
      binding.setTableName("ORDERS");
      binding.setLabelColumn(column);
      binding.setValueColumn(column);
      info.setListBindingInfo(binding);

      info.setSourceType(stype);
   }

   private static final String KEY =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "bug81696",
                     (IdentityID) null, "host-org").toIdentifier(true);
}
