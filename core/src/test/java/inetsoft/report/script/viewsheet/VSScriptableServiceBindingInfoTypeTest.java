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

package inetsoft.report.script.viewsheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.PropertyScriptable;
import inetsoft.web.binding.ScriptPropertyTool;
import inetsoft.web.binding.VSScriptableService;
import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.script.graal.ScriptFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78585: the chart/crosstab bindingInfo script members must be typed from
 * property-type.properties, and every typed function signature must match the bound method.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSScriptableServiceBindingInfoTypeTest {
   private static final String CHART_PREFIX = "Chart.bindingInfo.";
   private static final String CROSSTAB_PREFIX = "Crosstab.bindingInfo.";

   private VSChartBindingScriptable chartInfo;
   private VSCrosstabBindingScriptable crosstabInfo;

   @BeforeEach
   void setUp() {
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs-1");

      ChartVSAssembly chart = new ChartVSAssembly();
      ChartVSAssemblyInfo chartInfoModel = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      chartInfoModel.setName("chart1");
      chartInfoModel.getVSChartInfo().addXField(
         new VSChartDimensionRef(new AttributeRef("STATE")));
      viewsheet.addAssembly(chart);

      CrosstabVSAssembly crosstab = new CrosstabVSAssembly();
      crosstab.getVSAssemblyInfo().setName("crosstab1");
      viewsheet.addAssembly(crosstab);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs-1");
      when(box.getViewsheet()).thenReturn(viewsheet);

      ChartVSAScriptable chartScriptable = new ChartVSAScriptable(box);
      chartScriptable.setAssembly("chart1");
      chartScriptable.addProperties();
      chartInfo = (VSChartBindingScriptable) chartScriptable.getMember("bindingInfo");

      CrosstabVSAScriptable crosstabScriptable = new CrosstabVSAScriptable(box);
      crosstabScriptable.setAssembly("crosstab1");
      crosstabInfo = new VSCrosstabBindingScriptable(crosstabScriptable);
      crosstabInfo.init();
      crosstabInfo.setRowFields(new String[] { "r1" });
      crosstabInfo.setColFields(new String[] { "c1" });
      crosstabInfo.setMeasureFields(new String[] { "m1" });
   }

   @Test
   void chartFunctionSignaturesMatchBoundMethods() throws Exception {
      assertSignatures(chartInfo, CHART_PREFIX);
   }

   @Test
   void crosstabFunctionSignaturesMatchBoundMethods() throws Exception {
      assertSignatures(crosstabInfo, CROSSTAB_PREFIX);
   }

   @Test
   void serviceTypesBindingInfoFunctions() throws Exception {
      ObjectNode node = definition(chartInfo, CHART_PREFIX);
      assertEquals("fn(field: string, type: number) => bool",
                   node.get("isTopNReverse").get("!type").asText());
      assertEquals("fn(field: string, type: number) => number",
                   node.get("getTopN").get("!type").asText());
      assertTrue(node.get("setTopNReverse").get("!type").asText().startsWith("fn(field"));
   }

   @Test
   void valueMembersKeepInferredType() throws Exception {
      ObjectNode chart = definition(chartInfo, CHART_PREFIX);

      for(String name : new String[] { "xFields", "yFields", "breakdownFields" }) {
         assertEquals("[string]", chart.get(name).get("!type").asText(), name);
      }

      // crosstab field lists are Lists, which the service has always typed as "?"
      ObjectNode crosstab = definition(crosstabInfo, CROSSTAB_PREFIX);

      for(String name : new String[] { "rowFields", "colFields", "measureFields" }) {
         assertTrue(crosstab.get(name).has("!type"), name);
      }
   }

   private ObjectNode definition(PropertyScriptable scriptable, String prefix) throws Exception {
      VSScriptableService service = new VSScriptableService(null, null, null, null);
      Method method = VSScriptableService.class.getDeclaredMethod(
         "createBindingInfoDefinition", ObjectMapper.class, ObjectNode.class, ObjectNode.class,
         PropertyScriptable.class, String.class);
      method.setAccessible(true);
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode library = mapper.createObjectNode();
      library.set("!define", mapper.createObjectNode());
      ObjectNode node = mapper.createObjectNode();
      method.invoke(service, mapper, library, node, scriptable, prefix);
      return node;
   }

   private void assertSignatures(PropertyScriptable scriptable, String prefix) throws Exception {
      Field methodField = ScriptFunction.class.getDeclaredField("method");
      methodField.setAccessible(true);
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(Object key : scriptable.getMemberKeys()) {
         Object member = scriptable.getMember(String.valueOf(key));

         if(!(member instanceof ScriptFunction)) {
            continue;
         }

         checked++;
         String name = String.valueOf(key);
         String type = ScriptPropertyTool.getPropertyType(scriptable, prefix, name);
         Method method = (Method) methodField.get(member);

         if(type == null || !type.startsWith("fn(")) {
            problems.add(name + ": type is not a fn signature: " + type);
            continue;
         }

         String params = type.substring(3, type.indexOf(')'));
         int count = params.trim().isEmpty() ? 0 : params.split(",").length;

         if(method == null || count != method.getParameterCount()) {
            problems.add(name + ": " + type + " vs arity " +
                         (method == null ? "null method" : method.getParameterCount()));
         }
      }

      assertTrue(checked > 0);
      assertTrue(problems.isEmpty(), problems.toString());
   }
}
