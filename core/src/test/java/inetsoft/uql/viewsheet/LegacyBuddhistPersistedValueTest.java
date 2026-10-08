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
package inetsoft.uql.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.BatchAction;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.DataType;
import inetsoft.uql.tabular.QueryParameter;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import inetsoft.web.admin.deploy.PartialDeploymentJarInfo;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dates that the server formatted from a Date and saved before Bug #77605 hold Buddhist years
 * when they were written on a th_TH server. Besides the selections covered by
 * SelectionLegacyBuddhistViewsheetTest, they are read back as the same Gregorian dates when
 * date.legacy.buddhist.compat is on, and kept when it is off (Bug #78040): Text Input values
 * and bookmark state of a date type, edited embedded table cells, list input values, batch
 * action parameters, tabular query parameters, hyperlink parameters, date comparison custom
 * periods and the deployment date of a jar. Each object is written by its real writer, its
 * 1996 years are replaced by the Buddhist 2539 and the legacy XML is parsed by its real reader.
 * A Buddhist 2539-02-29 read as a Gregorian year rolls to 2539-02-28 or 2539-03-01.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegacyBuddhistPersistedValueTest {
   @AfterEach
   void restore() {
      SreeEnv.remove(COMPAT_PROPERTY);
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void viewsheetDesign(boolean on) throws Exception {
      String xml = legacy(toXML(buildViewsheet()));
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      Viewsheet vs = new Viewsheet();
      vs.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      TextInputVSAssemblyInfo tinfo = (TextInputVSAssemblyInfo) vs.getAssembly("TD").getInfo();
      assertDate(on, tinfo.getValue());
      // a string text input holds typed text, which is never rewritten
      TextInputVSAssemblyInfo sinfo = (TextInputVSAssemblyInfo) vs.getAssembly("TS").getInfo();
      assertEquals("2539-02-29", sinfo.getValue());
      assertEquals("2539-02-29", sinfo.getDefaultText());

      ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo) vs.getAssembly("CB").getInfo();
      assertDate(on, cinfo.getValues()[0]);
      // a label is display text
      assertEquals("2539-02-29", cinfo.getLabels()[0]);

      EmbeddedTableVSAssembly table = (EmbeddedTableVSAssembly) vs.getAssembly("ET");
      assertDate(on, table.getStateDataMap().get(new CellRef("d", 0)));
      assertEquals("2539-02-29", table.getStateDataMap().get(new CellRef("s", 0)));
   }

   // in a viewsheet the state of a text input replaces its value, so the info is read alone
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void textInputInfoValue(boolean on) throws Exception {
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();
      info.setDataType(XSchema.DATE);
      info.setValue(new java.sql.Date(date(1996, 2, 29)));
      TextInputVSAssemblyInfo info2 = new TextInputVSAssemblyInfo();
      parse(info2, legacy(toXML(info)), on);

      assertDate(on, info2.getValue());
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void viewsheetBookmarkState(boolean on) throws Exception {
      Viewsheet vs0 = buildViewsheet();
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs0.writeState(writer, false);
      writer.flush();
      String xml = legacy(buf.toString());
      assertTrue(xml.contains("2539-02-29"), xml);

      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      Viewsheet vs = buildViewsheet();
      ((TextInputVSAssembly) vs.getAssembly("TD")).setSelectedObject(null);
      ((EmbeddedTableVSAssembly) vs.getAssembly("ET")).setStateDataMap(null);
      vs.parseState(Tool.parseXML(new StringReader(xml)).getDocumentElement(), true);

      assertDate(on, ((TextInputVSAssembly) vs.getAssembly("TD")).getSelectedObject());
      assertEquals("2539-02-29", ((TextInputVSAssembly) vs.getAssembly("TS")).getSelectedObject());

      EmbeddedTableVSAssembly table = (EmbeddedTableVSAssembly) vs.getAssembly("ET");
      assertDate(on, table.getStateDataMap().get(new CellRef("d", 0)));
      assertEquals("2539-02-29", table.getStateDataMap().get(new CellRef("s", 0)));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void batchActionParameters(boolean on) throws Exception {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("d", new java.sql.Date(date(1996, 2, 29)));
      params.put("ts", new Timestamp(date(1996, 2, 29)));
      params.put("arr", new Object[] { new java.sql.Date(date(1996, 2, 29)) });
      params.put("dyn", new DynamicParameterValue(
         new java.sql.Date(date(1996, 2, 29)), DynamicValueModel.VALUE, XSchema.DATE));
      params.put("dynStr", new DynamicParameterValue(
         "1996-02-29", DynamicValueModel.VALUE, XSchema.STRING));
      params.put("exp", new DynamicParameterValue(
         "=\"1996-02-29\"", DynamicValueModel.EXPRESSION, XSchema.DATE));
      BatchAction action = new BatchAction();
      action.setTaskId("admin~;~host-org:task");
      action.setEmbeddedParameters(new ArrayList<>(List.of(params)));

      BatchAction action2 = new BatchAction();
      parse(action2, legacy(toXML(action)), on);
      Map<String, Object> map = action2.getEmbeddedParameters().get(0);

      assertDate(on, map.get("d"));
      assertInstanceOf(java.sql.Date.class, map.get("d"));
      assertDate(on, map.get("ts"));
      assertInstanceOf(Timestamp.class, map.get("ts"));
      assertDate(on, ((Object[]) map.get("arr"))[0]);

      // a dynamic value is kept as the string it was saved as
      Object dyn = ((DynamicParameterValue) map.get("dyn")).getValue();
      assertEquals(on ? "1996-02-29" : "2539-02-29", dyn);
      assertEquals("2539-02-29", ((DynamicParameterValue) map.get("dynStr")).getValue());
      assertEquals("=\"2539-02-29\"", ((DynamicParameterValue) map.get("exp")).getValue());
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void queryParameter(boolean on) throws Exception {
      QueryParameter param = new QueryParameter();
      param.setName("p");
      param.setType(DataType.DATE);
      param.setValue(new java.sql.Date(date(1996, 2, 29)));
      QueryParameter param2 = new QueryParameter();
      parse(param2, legacy(toXML(param)), on);

      assertDate(on, param2.getValue());
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void hyperlinkRefParameter(boolean on) throws Exception {
      Hyperlink.Ref ref = new Hyperlink.Ref("link");
      ref.setParameter("p", new java.sql.Date(date(1996, 2, 29)));
      ref.setParameter("s", "1996-02-29");
      Hyperlink.Ref ref2 = new Hyperlink.Ref();
      parse(ref2, legacy(toXML(ref)), on);

      assertDate(on, ref2.getParameter("p"));
      assertEquals("2539-02-29", ref2.getParameter("s"));
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void dateComparisonCustomPeriods(boolean on) throws Exception {
      DCNamedGroupInfo info = new DCNamedGroupInfo();
      info.addGroupName("P1");
      info.setGroupValue("P1", new ArrayList<>(
         List.of(new Date(date(1996, 2, 29)), new Date(date(1996, 3, 31)))));
      DCNamedGroupInfo info2 = new DCNamedGroupInfo();
      parse(info2, legacy(toXML(info)), on);

      List<?> values = info2.getGroupValue("P1");
      assertDate(on, values.get(0));
      assertEquals(on ? date(1996, 3, 31) : date(2539, 3, 31), ((Date) values.get(1)).getTime());
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void deploymentDate(boolean on) throws Exception {
      PartialDeploymentJarInfo info = new PartialDeploymentJarInfo();
      info.setName("jar");
      info.setDeploymentDate(new Timestamp(date(1996, 2, 29)));
      PartialDeploymentJarInfo info2 = new PartialDeploymentJarInfo();
      parse(info2, legacy(toXML(info)), on);

      assertDate(on, info2.getDeploymentDate());
   }

   private static Viewsheet buildViewsheet() {
      Viewsheet vs = new Viewsheet();

      // a text input bound to an embedded table date column has a date type
      TextInputVSAssembly td = new TextInputVSAssembly(vs, "TD");
      ((TextInputVSAssemblyInfo) td.getInfo()).setDataType(XSchema.DATE);
      td.setSelectedObject(new java.sql.Date(date(1996, 2, 29)));

      TextInputVSAssembly ts = new TextInputVSAssembly(vs, "TS");
      ((TextInputVSAssemblyInfo) ts.getInfo()).setDefaultTextValue("1996-02-29");
      ts.setSelectedObject("1996-02-29");

      ComboBoxVSAssembly cb = new ComboBoxVSAssembly(vs, "CB");
      ComboBoxVSAssemblyInfo cinfo = (ComboBoxVSAssemblyInfo) cb.getInfo();
      cinfo.setDataType(XSchema.DATE);
      ListData data = new ListData();
      data.setDataType(XSchema.DATE);
      data.setValues(new Object[] { new java.sql.Date(date(1996, 2, 29)) });
      data.setLabels(new String[] { "1996-02-29" });
      cinfo.setListData(data);
      cinfo.setValues(new Object[] { new java.sql.Date(date(1996, 2, 29)) });
      cinfo.setLabels(new String[] { "1996-02-29" });

      EmbeddedTableVSAssembly et = new EmbeddedTableVSAssembly(vs, "ET");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("d", XSchema.DATE));
      columns.addAttribute(column("s", XSchema.STRING));
      et.setColumnSelection(columns);
      Map<CellRef, Object> dmap = new HashMap<>();
      dmap.put(new CellRef("d", 0), new java.sql.Date(date(1996, 2, 29)));
      dmap.put(new CellRef("s", 0), "1996-02-29");
      et.setStateDataMap(dmap);

      for(VSAssembly assembly : new VSAssembly[] { td, ts, cb, et }) {
         vs.addAssembly(assembly);
      }

      return vs;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   private static void parse(XMLSerializable obj, String xml, boolean on) throws Exception {
      assertTrue(xml.contains("2539-"), xml);
      SreeEnv.setProperty(COMPAT_PROPERTY, String.valueOf(on));
      obj.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
   }

   // the output of a th_TH server before #77605
   private static String legacy(String xml) {
      return xml.replace("1996-", "2539-");
   }

   private static String toXML(Viewsheet vs) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      vs.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static String toXML(XMLSerializable obj) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      obj.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static void assertDate(boolean on, Object value) {
      assertInstanceOf(Date.class, value);
      long time = ((Date) value).getTime();

      if(on) {
         assertEquals(date(1996, 2, 29), time);
      }
      else {
         // CE 2539 has no Feb 29, the lenient parse rolls it
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTimeInMillis(time);
         assertEquals(2539, cal.get(Calendar.YEAR));
      }
   }

   private static long date(int year, int month, int day) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return cal.getTimeInMillis();
   }

   private static final String COMPAT_PROPERTY = "date.legacy.buddhist.compat";
}
