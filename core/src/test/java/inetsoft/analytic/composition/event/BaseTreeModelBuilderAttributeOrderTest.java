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
package inetsoft.analytic.composition.event;

import inetsoft.report.composition.AssetTreeModel;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81269: a logical model entity's own attribute list must keep the order defined
 * in the model, not get alphabetized by {@code BaseTreeModelBuilder.appendColumnNodes}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BaseTreeModelBuilderAttributeOrderTest {
   @Test
   void logicalModelEntityAttributesKeepModelOrder() {
      AssetTreeModel.Node root = new AssetTreeModel.Node();
      TestTreeModelBuilder builder = new TestTreeModelBuilder();
      builder.baseEntry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, "/TestModel", null);

      builder.appendColumnNodes(entityAssembly("Entity1"), root, true, false);

      assertEquals(List.of("Zcol", "Acol"), dimensionNames(root),
                   "a logical model entity's attributes must keep the model's own order");
      assertEquals(List.of("Mcol"), measureNames(root));
   }

   @Test
   void plainTableAttributesStayAlphabetical() {
      AssetTreeModel.Node root = new AssetTreeModel.Node();
      TestTreeModelBuilder builder = new TestTreeModelBuilder();
      builder.baseEntry = null;

      builder.appendColumnNodes(entityAssembly("Table1"), root, true, false);

      assertEquals(List.of("Acol", "Zcol"), dimensionNames(root),
                   "a non-logical-model source must still be alphabetized");
      assertEquals(List.of("Mcol"), measureNames(root));
   }

   /**
    * Guards against a side effect the refuter found in the original fix proposal: the
    * logic-model skip-sort condition must not also apply to addLMEntryLevel=true (the
    * calc-fields path used by {@code BaseTreeModelBuilder.addLMEntryLevel}), which has
    * no model-defined order and must keep sorting alphabetically as before.
    */
   @Test
   void logicalModelEntityCalcFieldsStayAlphabetical() {
      AssetTreeModel.Node root = new AssetTreeModel.Node();
      TestTreeModelBuilder builder = new TestTreeModelBuilder();
      builder.baseEntry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.LOGIC_MODEL, "/TestModel", null);
      builder.vs = new Viewsheet();
      builder.vs.addCalcField("Entity1", calcField("Zcalc"));
      builder.vs.addCalcField("Entity1", calcField("Acalc"));

      builder.appendColumnNodes(entityAssembly("Entity1"), root, true, true);

      assertEquals(List.of("Acalc", "Zcalc"), dimensionNames(root));
   }

   private static TableAssembly entityAssembly(String name) {
      TableAssembly assembly = new BoundTableAssembly(null, name);
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("Zcol", XSchema.STRING));
      cols.addAttribute(column("Acol", XSchema.STRING));
      cols.addAttribute(column("Mcol", XSchema.DOUBLE));
      assembly.setColumnSelection(cols, false);
      assembly.setColumnSelection(cols, true);
      return assembly;
   }

   private static ColumnRef column(String attr, String dataType) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, attr));
      ref.setDataType(dataType);
      return ref;
   }

   private static CalculateRef calcField(String attr) {
      CalculateRef ref = new CalculateRef();
      ref.setDataRef(new ExpressionRef(null, attr));
      ref.setDataType(XSchema.STRING);
      return ref;
   }

   private static List<String> dimensionNames(AssetTreeModel.Node root) {
      return columnNames(root.getNodes()[0]);
   }

   private static List<String> measureNames(AssetTreeModel.Node root) {
      return columnNames(root.getNodes()[1]);
   }

   private static List<String> columnNames(AssetTreeModel.Node folder) {
      List<String> names = new ArrayList<>();

      for(AssetTreeModel.Node node : folder.getNodes()) {
         names.add(node.getEntry().getProperty("attribute"));
      }

      return names;
   }

   private static class TestTreeModelBuilder extends BaseTreeModelBuilder {
   }
}
