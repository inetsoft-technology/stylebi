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
package inetsoft.mv;

import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.mv.trans.TransformationFault;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81659: a viewsheet bound to a cube table (rejected by the MV analyzer) with an
 * unresolved aggregate binding made analyze() fail with a raw NullPointerException from
 * checkCombinable. The rejected assembly is already reported by the cube fault, so the
 * analysis must finish; an unresolved aggregate on an accepted table must fail with a
 * clear message instead of a NullPointerException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSMVAnalyzerCubeBindingTest {
   @Test
   void cubeTableIsNotAcceptableAndReportsCubeFault() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new CubeTableAssembly(ws, "CubeT"));
      Viewsheet vs = viewsheet(ws);
      VSMVAnalyzer analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", vs, null, null, false, null);
      TransformationDescriptor desc = new TransformationDescriptor(vs, ws, 1);
      desc.reset("Crosstab1", "CubeT", "CubeT", "CubeT");

      Object info = tableInfo(analyzer, "CubeT", crosstab(vs));
      Method method = VSMVAnalyzer.class.getDeclaredMethod(
         "isAcceptable", Worksheet.class, info.getClass(), TransformationDescriptor.class);
      method.setAccessible(true);

      assertFalse((Boolean) method.invoke(analyzer, ws, info, desc));
      assertFalse(desc.isEmptyFault());
   }

   @Test
   void analyzeSkipsAssemblyBoundToRejectedCubeTable() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new CubeTableAssembly(ws, "CubeT"));
      Viewsheet vs = viewsheet(ws);
      crosstab(vs);
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getAllVariables()).thenReturn(new VariableTable());
      VSMVAnalyzer analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", vs, null, box, false, null);

      MVDef[] defs = analyzer.analyze();

      assertEquals(0, defs.length);
      List<TransformationFault> faults =
         analyzer.getDescriptor().getFaults("Crosstab1");
      assertFalse(faults.isEmpty());
      assertNull(analyzer.getDescriptor().getNotHitMVHints().get("Crosstab1"));
   }

   @Test
   void nullAggregateRefFailsWithClearMessage() {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs);

      MessageException ex = assertThrows(
         MessageException.class, () -> MVDef.getAggregateInfo(crosstab));
      assertTrue(ex.getMessage().contains("Crosstab1"), ex.getMessage());
      assertTrue(ex.getMessage().contains("unresolved"), ex.getMessage());
   }

   @Test
   void nullAggregateRefWithApplyAliasFailsWithClearMessage() {
      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs);
      VSAggregateRef aggr = (VSAggregateRef)
         crosstab.getVSCrosstabInfo().getRuntimeAggregates()[0];
      aggr.setApplyAlias(true);

      MessageException ex = assertThrows(
         MessageException.class, () -> MVDef.getAggregateInfo(crosstab));
      assertTrue(ex.getMessage().contains("Crosstab1"), ex.getMessage());
   }

   @Test
   void acceptedTableWithoutDefKeepsNotHitMvHint() throws Exception {
      // an accepted table for which createMV creates no def must still get its hints
      Worksheet ws = new Worksheet();
      Viewsheet vs = viewsheet(ws);
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "T1"));
      VSAggregateRef aggr = new VSAggregateRef();
      aggr.setDataRef(new ColumnRef(new AttributeRef(null, "total")));
      aggr.setFormula(AggregateFormula.MEDIAN);
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setRuntimeAggregates(new DataRef[] { aggr });
      crosstab.setVSCrosstabInfo(cinfo);
      vs.addAssembly(crosstab);

      VSMVAnalyzer analyzer = new VSMVAnalyzer("1^128^__NULL__^vs", vs, null, null, false, null);
      Method prepare = VSMVAnalyzer.class.getDeclaredMethod("prepare");
      prepare.setAccessible(true);
      prepare.invoke(analyzer);
      analyzer.getDescriptor();

      Class<?> infoClass = Class.forName("inetsoft.mv.VSMVAnalyzer$TableInfo");
      Method check = VSMVAnalyzer.class.getDeclaredMethod(
         "checkCombinable", MVDef[].class, java.lang.reflect.Array.newInstance(infoClass, 0).getClass());
      check.setAccessible(true);
      check.invoke(analyzer, new MVDef[0], java.lang.reflect.Array.newInstance(infoClass, 0));

      assertNotNull(analyzer.getDescriptor().getNotHitMVHints().get("Crosstab1"));
   }

   private static Viewsheet viewsheet(Worksheet ws) {
      return new Viewsheet() {
         @Override
         public Worksheet getBaseWorksheet() {
            return ws;
         }
      };
   }

   private static CrosstabVSAssembly crosstab(Viewsheet vs) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "CubeT"));
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setRuntimeAggregates(new DataRef[] { new VSAggregateRef() });
      crosstab.setVSCrosstabInfo(cinfo);
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static Object tableInfo(VSMVAnalyzer analyzer, String table, VSAssembly assembly)
      throws Exception
   {
      Class<?> cls = Class.forName("inetsoft.mv.VSMVAnalyzer$TableInfo");
      var ctor = cls.getDeclaredConstructor(String.class);
      ctor.setAccessible(true);
      Object info = ctor.newInstance(table);
      Method add = cls.getDeclaredMethod("add", VSAssembly.class);
      add.setAccessible(true);
      add.invoke(info, assembly);
      return info;
   }
}
