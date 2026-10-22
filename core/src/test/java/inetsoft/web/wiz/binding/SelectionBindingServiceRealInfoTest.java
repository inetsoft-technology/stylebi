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
package inetsoft.web.wiz.binding;

import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.SelectionTreeVSAssembly;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #79168 against real {@code SelectionTreeVSAssemblyInfo} objects, so the single-select level
 * getter's own fallback and mixed-level semantics are exercised, not stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionBindingServiceRealInfoTest {
   @Test
   void anIdModeTreeReboundToColumnsReportsNoSingleSelectLevels() throws Exception {
      // Real info: with singleSelection on and no stored level, an ID-mode tree's getter falls back
      // to the ID columns' names, which must not be reported as dropped levels.
      SelectionTreeVSAssemblyInfo idInfo = new SelectionTreeVSAssemblyInfo();
      idInfo.setMode(SelectionTreeVSAssemblyInfo.ID);
      idInfo.setDataRefs(columnRefs("PARENT_ID", "ID", "LABEL"));
      idInfo.setSingleSelectionValue(true);
      assertNotNull(idInfo.getSingleSelectionLevelNames());

      SelectionTreeVSAssemblyInfo columnInfo = new SelectionTreeVSAssemblyInfo();
      columnInfo.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
      columnInfo.setDataRefs(columnRefs("STATE", "CITY"));
      columnInfo.setSingleSelectionValue(true);

      Map<String, Object> result = rebindTreeLevels(
         treeWithInfos(idInfo, columnInfo), List.of("STATE", "CITY"));

      assertFalse(result.containsKey("droppedSingleSelectionLevels"));
      assertFalse(result.containsKey("singleSelectionLevels"));
   }

   @Test
   void disclosesTheLevelARealInfoLosesWhenItsColumnIsRebound() throws Exception {
      SelectionTreeVSAssemblyInfo before = new SelectionTreeVSAssemblyInfo();
      before.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
      before.setDataRefs(columnRefs("STATE", "ORDER_DATE", "COMPANY"));
      before.setSingleSelectionValue(true);
      before.setSingleSelectionLevelNames(List.of("COMPANY"));
      assertEquals(List.of("COMPANY"), before.getSingleSelectionLevelNames());

      // What the dialog apply does: new refs first, then the stored names resolved against them.
      SelectionTreeVSAssemblyInfo after = new SelectionTreeVSAssemblyInfo();
      after.setMode(SelectionTreeVSAssemblyInfo.COLUMN);
      after.setDataRefs(columnRefs("STATE", "ORDER_DATE", "COMPANY"));
      after.setSingleSelectionValue(true);
      after.setSingleSelectionLevelNames(List.of("COMPANY"));
      after.setDataRefs(columnRefs("STATE", "ORDER_DATE", "CITY"));
      after.setSingleSelectionLevelNames(List.of("COMPANY"));

      Map<String, Object> result = rebindTreeLevels(
         treeWithInfos(before, after), List.of("STATE", "ORDER_DATE", "CITY"));

      assertEquals(List.of("COMPANY"), result.get("droppedSingleSelectionLevels"));
      assertEquals(List.of(), result.get("singleSelectionLevels"));
   }

   private static DataRef[] columnRefs(String... names) {
      return Arrays.stream(names).map(n -> new ColumnRef(new AttributeRef(null, n)))
         .toArray(DataRef[]::new);
   }
}
