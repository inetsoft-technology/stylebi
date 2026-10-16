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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.XCube;
import inetsoft.uql.XDimension;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.table.DrillLevel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76861: a custom drill hierarchy on a non-date column must resolve against the runtime
 * dimension refs a crosstab/chart really has after {@code box.getData}. The member's date option
 * is meaningless for a non-date column, so it must not have to equal the runtime ref's date
 * level (5 by default, 0 when the ref asks for "none"). The refs are never given a hand-set
 * date level, so the runtime default is what is exercised.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class HierarchyDrillRealChainTest {
   // null = the ref's date level is left unset
   private static final String[] REF_LEVELS = { null, "-1", "5", "0" };
   private static final int[] MEMBER_OPTIONS = { DateRangeRef.NONE_INTERVAL,
                                                 DateRangeRef.YEAR_INTERVAL };

   @Test
   void crosstabNonDateHierarchyResolvesForEveryRefLevelAndMemberOption() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      int n = 0;

      for(String refLevel : REF_LEVELS) {
         for(int option : MEMBER_OPTIONS) {
            for(boolean reloaded : new boolean[] { false, true }) {
               String name = "Crosstab" + n++;
               VSCube cube = cube(option);
               CrosstabVSAssembly ct = crosstab(vs, name, refLevel);
               ct.setXCube(reloaded ? roundTrip(cube) : cube);
               box.getData(name);

               VSDimensionRef ref = (VSDimensionRef)
                  ct.getVSCrosstabInfo().getRuntimeRowHeaders()[0];
               String msg = "crosstab refLevel=" + refLevel + " option=" + option +
                  " reloaded=" + reloaded + " runtimeLevel=" + ref.getDateLevel();
               assertDrills(ct.getXCube(), ref, new DataRef[] { ref }, msg, true);
            }
         }
      }
   }

   @Test
   void chartNonDateHierarchyResolvesForEveryRefLevelAndMemberOption() throws Exception {
      ViewsheetSandbox box = vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      int n = 0;

      for(String refLevel : REF_LEVELS) {
         for(int option : MEMBER_OPTIONS) {
            for(boolean reloaded : new boolean[] { false, true }) {
               String name = "Chart" + n++;
               VSCube cube = cube(option);
               ChartVSAssembly chart = chart(vs, name, refLevel);
               chart.setXCube(reloaded ? roundTrip(cube) : cube);
               box.getData(name);

               VSDimensionRef ref = (VSDimensionRef) chart.getVSChartInfo().getRTXFields()[0];
               String msg = "chart refLevel=" + refLevel + " option=" + option +
                  " reloaded=" + reloaded + " runtimeLevel=" + ref.getDateLevel();
               assertDrills(chart.getXCube(), ref, null, msg, false);
            }
         }
      }
   }

   private static void assertDrills(XCube cube, VSDimensionRef ref, DataRef[] refs, String msg,
                                    boolean crosstab)
   {
      if(crosstab) {
         assertEquals("+", VSUtil.getCrosstabDrillOp(cube, ref, refs), msg + " drillOp");
      }

      assertEquals(DrillLevel.Root, VSUtil.getDrillLevel(ref, cube), msg + " drillLevel");
      VSDimensionRef next = VSUtil.getCubeNextLevelRef(ref, cube);
      assertNotNull(next, msg + " next level");
      assertEquals("wind", next.getGroupColumnValue(), msg + " next level column");
   }

   private static VSCube cube(int option) {
      AttributeRef type = new AttributeRef(null, "type");
      type.setDataType(XSchema.STRING);
      AttributeRef wind = new AttributeRef(null, "wind");
      wind.setDataType(XSchema.STRING);

      VSDimension dimension = new VSDimension();
      dimension.setName("TypeWind");

      for(AttributeRef attr : List.of(type, wind)) {
         VSDimensionMember member = new VSDimensionMember();
         member.setDataRef(attr);
         member.setDateOption(option);
         dimension.addLevel(member);
      }

      VSCube cube = new VSCube();
      cube.setDimensions(List.<XDimension>of(dimension));
      return cube;
   }

   // the saved-and-reloaded form of the cube
   private static VSCube roundTrip(VSCube cube) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      cube.writeXML(writer);
      writer.flush();
      Element elem = Tool.parseXML(
         new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      VSCube copy = new VSCube();
      copy.parseXML(elem);
      return copy;
   }

   private static CrosstabVSAssembly crosstab(Viewsheet vs, String name, String refLevel) {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, name);
      crosstab.setSourceInfo(source());
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");

      if(refLevel != null) {
         dim.setDateLevelValue(refLevel);
      }

      cinfo.setDesignRowHeaders(new DataRef[] { dim });
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static ChartVSAssembly chart(Viewsheet vs, String name, String refLevel) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(source());
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "type")));
      dim.setGroupColumnValue("type");

      if(refLevel != null) {
         dim.setDateLevelValue(refLevel);
      }

      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);
      return chart;
   }

   private static SourceInfo source() {
      return new SourceInfo(SourceInfo.VS_ASSEMBLY, null, Assembly.TABLE_VS_BOUND + TABLE);
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   private static final String TABLE = "TableView1";
}
