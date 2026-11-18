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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.mv.trans.TableNode;
import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.mv.trans.SelectionUpTransformer;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XFilterNodeItem;
import inetsoft.util.Tool;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.annotation.DirtiesContext;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80152. The condition reaches the MV query as the flattened ref the MV transformer
 * (SelectionUpTransformer.moveSelectionUp0) produces for a viewsheet mirror table over the
 * worksheet table (PRODUCTS1 over PRODUCTS1_O): a plain AttributeRef of the child table. The
 * column kind must be recovered from the child table, not from the flattened ref.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVConditionListHandlerFlattenedRefTest {
   private static final String[] NAMES = {
      "MeToo AppServer", "True Action", "banana", "Kiwi", "Plum" };

   @Test
   void scriptExpressionColumnIsCaseInsensitiveAfterTheTransformerFlattensIt() throws Exception {
      ExpressionRef exp = new ExpressionRef(null, "E");
      exp.setExpression("field['PRODUCT_NAME'] + ''");
      ColumnRef col = new ColumnRef(exp);
      col.setSQL(false);
      assertEquals(List.of("Kiwi", "Plum"), keptRows(col));
   }

   @Test
   void physicalColumnStaysCaseSensitiveAfterTheTransformerFlattensIt() throws Exception {
      assertEquals(List.of("Kiwi", "MeToo AppServer", "Plum", "True Action"),
                   keptRows(new ColumnRef(new AttributeRef("PRODUCT_NAME"))));
   }

   @Test
   void sqlExpressionColumnStaysDbEvaluatedWhenFlagIsOnTheColumn() throws Exception {
      // worksheet SQL expression: ColumnRef sql=true over a plain ExpressionRef
      ExpressionRef exp = new ExpressionRef(null, "E");
      exp.setExpression("upper(PRODUCT_NAME)");
      ColumnRef col = new ColumnRef(exp);
      col.setSQL(true);
      assertEquals(List.of("Kiwi", "MeToo AppServer", "Plum", "True Action"), keptRows(col));
   }

   @Test
   void sqlExpressionColumnStaysDbEvaluatedWhenFlagIsOnTheExpression() throws Exception {
      ExpressionRef exp = new ExpressionRef(null, "E") {
         @Override
         public boolean isSQL() {
            return true;
         }
      };
      ColumnRef col = new ColumnRef(exp);
      col.setSQL(false);
      assertEquals(List.of("Kiwi", "MeToo AppServer", "Plum", "True Action"), keptRows(col));
   }

   @Test
   void unflattenedRefsAreClassifiedByTheirOwnKind() {
      ExpressionRef script = new ExpressionRef(null, "E");
      ColumnRef scol = new ColumnRef(script);
      scol.setSQL(false);
      assertFalse(MVConditionListHandler.isDbEvaluated(scol));
      scol.setSQL(true);
      assertTrue(MVConditionListHandler.isDbEvaluated(scol));
   }

   /**
    * Build PRODUCTS1 (mirror) over PRODUCTS1_O (bound, holds the real column), put a variable
    * NOT CONTAINS "a" pre-runtime condition on the child's column E, move it up with the real
    * transformer, and run the moved condition through the real handler and dictionary.
    */
   private static List<String> keptRows(ColumnRef ecol) throws Exception {
      String old = SreeEnv.getProperty("string.compare.casesensitive");
      SreeEnv.setProperty("string.compare.casesensitive", "false");
      Tool.invalidateCaseSensitive();

      try {
         Worksheet ws = new Worksheet();
         PhysicalBoundTableAssembly child = new PhysicalBoundTableAssembly(ws, "PRODUCTS1_O");
         ColumnSelection cols = new ColumnSelection();
         cols.addAttribute(ecol);
         child.setColumnSelection(cols);
         ws.addAssembly(child);

         MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "PRODUCTS1", child);
         ws.addAssembly(mirror);
         mirror.updateColumnSelection();

         Condition cond = new Condition();
         cond.setOperation(XCondition.CONTAINS);
         cond.addValue("a");
         cond.setNegated(true);
         ConditionList list = new ConditionList();
         list.append(new ConditionItem(ecol, cond, 0));
         child.setPreRuntimeConditionList(list);

         Method method = SelectionUpTransformer.class.getDeclaredMethod(
            "moveSelectionUp0", XNode.class, XNode.class, TransformationDescriptor.class);
         method.setAccessible(true);
         method.invoke(new SelectionUpTransformer(), new TableNode(mirror), new TableNode(child),
                       mock(TransformationDescriptor.class));

         ConditionList moved = mirror.getPreRuntimeConditionList().getConditionList();
         assertEquals(1, moved.getSize());
         DataRef flat = moved.getAttribute(0);
         assertTrue(((ColumnRef) flat).getDataRef() instanceof AttributeRef,
                    "the transformer flattens the condition column");

         SQLHelper helper = mock(SQLHelper.class);
         when(helper.getLikeCaseSensitivity()).thenReturn(Boolean.TRUE);
         MVConditionListHandler handler = new MVConditionListHandler(
            mirror, mock(MV.class), new VariableTable(), mock(MVDef.class));
         handler.setLikeCaseSensitive(MVConditionListHandler.getLikeCaseSensitivity(mirror, helper));

         XFilterNodeItem item = handler.createItem((ConditionItem) moved.getItem(0), new VariableTable());
         XBinaryCondition node = (XBinaryCondition) item.getNode();

         XDimDictionary dict = new XDimDictionary();
         dict.setDataType(String.class);

         for(String name : NAMES) {
            dict.addValue(name);
         }

         dict.complete();
         dict.fixFilter(node);

         List<String> matching = new ArrayList<>();

         for(Object idx : (Object[]) node.getExpression2().getValue()) {
            matching.add(dict.getValue(Integer.parseInt((String) idx)).toString());
         }

         assertTrue(node.isIsNot());
         List<String> kept = new ArrayList<>(List.of(NAMES));
         kept.removeAll(matching);
         kept.sort(null);
         return kept;
      }
      finally {
         if(old == null) {
            SreeEnv.remove("string.compare.casesensitive");
         }
         else {
            SreeEnv.setProperty("string.compare.casesensitive", old);
         }

         Tool.invalidateCaseSensitive();
      }
   }
}
