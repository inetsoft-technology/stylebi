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
package inetsoft.uql.tabular;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.web.WebConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The class names in a tabular editor's JSON come from the request, so the deserializer must
 * not load, initialize or construct them (Bug #77422), while the values the server writes
 * for data source properties must still round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabularEditorDeserializerTest {
   @ParameterizedTest(name = "{0} {1} {2}")
   @CsvSource({
      "TEXT,   SentinelText,        ,",
      "INT,    SentinelInt,         ,",
      "DOUBLE, SentinelDouble,      ,",
      "TAGS,   SentinelTags,        ,",
      "CUSTOM, SentinelCustom,      ,",
      "COLUMN, SentinelColumn,      ,",
      "DATE,   SentinelDate,        ,",
      "NONE,   SentinelNoType,      ,",
      "LIST,   [SentinelArray,      SentinelArray",
      "LIST,   java.util.ArrayList, SentinelElement",
      "LIST,   SentinelCollection,  java.lang.String",
   })
   void classNamedInRequestIsNotInitializedOrConstructed(String type, String propertyType,
                                                        String propertySubtype)
      throws Exception
   {
      ObjectNode node = mapper.createObjectNode();

      if(!"NONE".equals(type)) {
         node.put("type", type);
      }

      node.put("propertyType", sentinelName(propertyType));

      if(propertySubtype != null) {
         node.put("propertySubtype", sentinelName(propertySubtype));
      }

      if("LIST".equals(type)) {
         node.putArray("value").addObject().put("foo", "bar");
      }
      else if("DATE".equals(type)) {
         node.put("value", "2026-09-30 00:00:00");
      }
      else {
         node.putObject("value").put("foo", "bar");
      }

      try {
         mapper.treeToValue(node, TabularEditor.class);
      }
      catch(Exception ignore) {
         // a value that does not match its editor type may fail to parse
      }

      for(String name : new String[] { propertyType, propertySubtype }) {
         if(name != null && name.contains("Sentinel")) {
            String sentinel = name.replace("[", "");
            assertFalse(INITIALIZED.contains(sentinel), sentinel + " static initializer ran");
            assertFalse(CONSTRUCTED.contains(sentinel), sentinel + " constructor ran");
         }
      }
   }

   @Test
   void allPropertyTypesRoundTrip() throws Exception {
      RoundTripBean bean = new RoundTripBean();
      bean.setText("text");
      bean.setFlag(true);
      bean.setIntValue(42);
      bean.setLongValue(1234567890123L);
      bean.setShortValue((short) 7);
      bean.setByteValue((byte) 3);
      bean.setFloatValue(1.5f);
      bean.setDoubleValue(2.25);
      bean.setCharValue('x');
      bean.setTagValue(2);
      bean.setBoxedInt(9);
      bean.setDate(new Date(1_790_000_000_000L));
      bean.setFile(new File("77422.csv").getAbsoluteFile());
      bean.setStrings(new String[] { "a", "b" });
      bean.setStringList(new ArrayList<>(List.of("c", "d")));

      ColumnDefinition column = new ColumnDefinition();
      column.setName("col1");
      column.setAlias("alias1");
      column.setType(DataType.INTEGER);
      column.setSelected(true);
      bean.setColumns(new ColumnDefinition[] { column });

      QueryParameter parameter = new QueryParameter();
      parameter.setName("p1");
      parameter.setType(DataType.STRING);
      parameter.setValue("v1");
      bean.setParameter(parameter);

      HttpParameter header = new HttpParameter();
      header.setName("h1");
      header.setValue("hv1");
      header.setType(HttpParameter.ParameterType.HEADER);
      bean.setHttpParameter(header);
      bean.setHttpParameters(new HttpParameter[] { header });

      RestParameter restParameter = new RestParameter();
      restParameter.setName("r1");
      restParameter.setValue("rv1");
      RestParameters restParameters = new RestParameters();
      restParameters.setEndpoint("/endpoint");
      restParameters.setParameters(new ArrayList<>(List.of(restParameter)));
      bean.setRestParameters(restParameters);

      GoogleFile googleFile = new GoogleFile();
      googleFile.setName("sheet");
      googleFile.setId("id1");
      GooglePicker picker = new GooglePicker(googleFile);
      picker.setOauthToken("token");
      bean.setPicker(picker);

      bean.setMode(Mode.SECOND);
      bean.setModes(new Mode[] { Mode.FIRST, Mode.SECOND });

      TabularView view;

      try(MockedStatic<Config> config = mockStatic(Config.class)) {
         config.when(Config::getConfig).thenReturn(mock(Config.class));
         view = new LayoutCreator().createLayout(bean);
      }

      String json = mapper.writeValueAsString(view);
      TabularView result = mapper.readValue(json, TabularView.class);
      RoundTripBean copy = new RoundTripBean();
      TabularUtil.setValuesToBean(
         result.getViews(), copy, TabularUtil.getPropertyMap(RoundTripBean.class));

      assertEquals(TabularEditor.Type.TAGS, findEditor(result, "tagValue").getType());
      assertEquals(Integer.class, findEditor(result, "tagValue").getValue().getClass());
      assertEquals(String.class, findEditor(result, "mode").getValue().getClass());
      assertNull(findEditor(result, "modes").getValue());

      assertEquals("text", copy.getText());
      assertTrue(copy.isFlag());
      assertEquals(42, copy.getIntValue());
      assertEquals(1234567890123L, copy.getLongValue());
      assertEquals((short) 7, copy.getShortValue());
      assertEquals((byte) 3, copy.getByteValue());
      assertEquals(1.5f, copy.getFloatValue());
      assertEquals(2.25, copy.getDoubleValue());
      assertEquals('x', copy.getCharValue());
      assertEquals(2, copy.getTagValue());
      assertEquals(9, copy.getBoxedInt());
      assertEquals(bean.getDate(), copy.getDate());
      assertEquals(bean.getFile(), copy.getFile());
      assertArrayEquals(bean.getStrings(), copy.getStrings());
      assertEquals(bean.getStringList(), copy.getStringList());
      assertArrayEquals(bean.getColumns(), copy.getColumns());
      assertEquals(mapper.writeValueAsString(parameter),
                   mapper.writeValueAsString(copy.getParameter()));
      assertEquals(header, copy.getHttpParameter());
      assertArrayEquals(bean.getHttpParameters(), copy.getHttpParameters());
      assertEquals(restParameters, copy.getRestParameters());
      assertEquals(googleFile, copy.getPicker().getSelectedFile());
      assertEquals("token", copy.getPicker().getOauthToken());
      assertEquals(Mode.SECOND, copy.getMode());
   }

   @Test
   void unknownClassNamesDegradeToString() throws Exception {
      TabularEditor editor = mapper.readValue(
         "{\"type\":\"TEXT\",\"propertyType\":\"com.example.plugin.AuthType\"," +
         "\"value\":\"BASIC\"}", TabularEditor.class);
      assertEquals("BASIC", editor.getValue());

      editor = mapper.readValue(
         "{\"type\":\"TAGS\",\"propertyType\":\"" + Mode.class.getName() + "\"," +
         "\"value\":\"FIRST\"}", TabularEditor.class);
      assertEquals("FIRST", editor.getValue());

      editor = mapper.readValue(
         "{\"type\":\"DATE\",\"propertyType\":\"com.example.plugin.Day\"," +
         "\"value\":\"2026-09-30\"}", TabularEditor.class);
      assertEquals("2026-09-30", editor.getValue());

      editor = mapper.readValue(
         "{\"type\":\"LIST\",\"propertyType\":\"[Lcom.example.plugin.AuthType;\"," +
         "\"propertySubtype\":\"com.example.plugin.AuthType\",\"value\":[\"BASIC\"]}",
         TabularEditor.class);
      assertNull(editor.getValue());

      editor = mapper.readValue(
         "{\"type\":\"LIST\",\"propertyType\":\"java.util.List\"," +
         "\"propertySubtype\":\"com.example.plugin.AuthType\",\"value\":[\"BASIC\"]}",
         TabularEditor.class);
      assertEquals(List.of("BASIC"), editor.getValue());
   }

   private static String sentinelName(String name) {
      String prefix = "";

      if(name.startsWith("[")) {
         prefix = "[L";
         name = name.substring(1);
      }

      if(name.startsWith("Sentinel")) {
         name = TabularEditorDeserializerTest.class.getName() + "$" + name;
      }

      return prefix.isEmpty() ? name : prefix + name + ";";
   }

   private static TabularEditor findEditor(TabularView view, String property) {
      for(TabularView child : view.getViews()) {
         if(property.equals(child.getValue()) && child.getEditor() != null) {
            return child.getEditor();
         }

         TabularEditor editor = findEditor(child, property);

         if(editor != null) {
            return editor;
         }
      }

      return null;
   }

   private final ObjectMapper mapper = new WebConfig().objectMapper();

   private static final Set<String> INITIALIZED = ConcurrentHashMap.newKeySet();
   private static final Set<String> CONSTRUCTED = ConcurrentHashMap.newKeySet();

   public static class Sentinel {
      public Sentinel() {
         CONSTRUCTED.add(getClass().getSimpleName());
      }

      public void setFoo(String foo) {
      }
   }

   public static class SentinelText extends Sentinel {
      static { INITIALIZED.add("SentinelText"); }
   }

   public static class SentinelInt extends Sentinel {
      static { INITIALIZED.add("SentinelInt"); }
   }

   public static class SentinelDouble extends Sentinel {
      static { INITIALIZED.add("SentinelDouble"); }
   }

   public static class SentinelTags extends Sentinel {
      static { INITIALIZED.add("SentinelTags"); }
   }

   public static class SentinelCustom extends Sentinel {
      static { INITIALIZED.add("SentinelCustom"); }
   }

   public static class SentinelColumn extends Sentinel {
      static { INITIALIZED.add("SentinelColumn"); }
   }

   public static class SentinelDate extends Sentinel {
      static { INITIALIZED.add("SentinelDate"); }
   }

   public static class SentinelNoType extends Sentinel {
      static { INITIALIZED.add("SentinelNoType"); }
   }

   public static class SentinelArray extends Sentinel {
      static { INITIALIZED.add("SentinelArray"); }
   }

   public static class SentinelElement extends Sentinel {
      static { INITIALIZED.add("SentinelElement"); }
   }

   public static class SentinelCollection extends ArrayList<Object> {
      static { INITIALIZED.add("SentinelCollection"); }

      public SentinelCollection() {
         CONSTRUCTED.add("SentinelCollection");
      }
   }

   // stands in for an enum property type defined in a connector plugin
   public enum Mode {
      FIRST, SECOND
   }

   @View(vertical = true, value = {
      @View1("text"),
      @View1("flag"),
      @View1("intValue"),
      @View1("longValue"),
      @View1("shortValue"),
      @View1("byteValue"),
      @View1("floatValue"),
      @View1("doubleValue"),
      @View1("charValue"),
      @View1("tagValue"),
      @View1("boxedInt"),
      @View1("date"),
      @View1("file"),
      @View1("strings"),
      @View1("stringList"),
      @View1("columns"),
      @View1("parameter"),
      @View1("httpParameter"),
      @View1("httpParameters"),
      @View1("restParameters"),
      @View1("picker"),
      @View1("mode"),
      @View1("modes")
   })
   public static class RoundTripBean {
      @Property
      public String getText() {
         return text;
      }

      public void setText(String text) {
         this.text = text;
      }

      @Property
      public boolean isFlag() {
         return flag;
      }

      public void setFlag(boolean flag) {
         this.flag = flag;
      }

      @Property
      public int getIntValue() {
         return intValue;
      }

      public void setIntValue(int intValue) {
         this.intValue = intValue;
      }

      @Property
      public long getLongValue() {
         return longValue;
      }

      public void setLongValue(long longValue) {
         this.longValue = longValue;
      }

      @Property
      public short getShortValue() {
         return shortValue;
      }

      public void setShortValue(short shortValue) {
         this.shortValue = shortValue;
      }

      @Property
      public byte getByteValue() {
         return byteValue;
      }

      public void setByteValue(byte byteValue) {
         this.byteValue = byteValue;
      }

      @Property
      public float getFloatValue() {
         return floatValue;
      }

      public void setFloatValue(float floatValue) {
         this.floatValue = floatValue;
      }

      @Property
      public double getDoubleValue() {
         return doubleValue;
      }

      public void setDoubleValue(double doubleValue) {
         this.doubleValue = doubleValue;
      }

      @Property
      public char getCharValue() {
         return charValue;
      }

      public void setCharValue(char charValue) {
         this.charValue = charValue;
      }

      @Property
      @PropertyEditor(tags = { "1", "2" })
      public int getTagValue() {
         return tagValue;
      }

      public void setTagValue(int tagValue) {
         this.tagValue = tagValue;
      }

      @Property
      public Integer getBoxedInt() {
         return boxedInt;
      }

      public void setBoxedInt(Integer boxedInt) {
         this.boxedInt = boxedInt;
      }

      @Property
      public Date getDate() {
         return date;
      }

      public void setDate(Date date) {
         this.date = date;
      }

      @Property
      public File getFile() {
         return file;
      }

      public void setFile(File file) {
         this.file = file;
      }

      @Property
      public String[] getStrings() {
         return strings;
      }

      public void setStrings(String[] strings) {
         this.strings = strings;
      }

      @Property
      public List<String> getStringList() {
         return stringList;
      }

      public void setStringList(List<String> stringList) {
         this.stringList = stringList;
      }

      @Property
      public ColumnDefinition[] getColumns() {
         return columns;
      }

      public void setColumns(ColumnDefinition[] columns) {
         this.columns = columns;
      }

      @Property
      public QueryParameter getParameter() {
         return parameter;
      }

      public void setParameter(QueryParameter parameter) {
         this.parameter = parameter;
      }

      @Property
      public HttpParameter getHttpParameter() {
         return httpParameter;
      }

      public void setHttpParameter(HttpParameter httpParameter) {
         this.httpParameter = httpParameter;
      }

      @Property
      public HttpParameter[] getHttpParameters() {
         return httpParameters;
      }

      public void setHttpParameters(HttpParameter[] httpParameters) {
         this.httpParameters = httpParameters;
      }

      @Property
      public RestParameters getRestParameters() {
         return restParameters;
      }

      public void setRestParameters(RestParameters restParameters) {
         this.restParameters = restParameters;
      }

      @Property
      public GooglePicker getPicker() {
         return picker;
      }

      public void setPicker(GooglePicker picker) {
         this.picker = picker;
      }

      @Property
      public Mode getMode() {
         return mode;
      }

      public void setMode(Mode mode) {
         this.mode = mode;
      }

      @Property
      public Mode[] getModes() {
         return modes;
      }

      public void setModes(Mode[] modes) {
         this.modes = modes;
      }

      private String text;
      private boolean flag;
      private int intValue;
      private long longValue;
      private short shortValue;
      private byte byteValue;
      private float floatValue;
      private double doubleValue;
      private char charValue;
      private int tagValue;
      private Integer boxedInt;
      private Date date;
      private File file;
      private String[] strings;
      private List<String> stringList;
      private ColumnDefinition[] columns;
      private QueryParameter parameter;
      private HttpParameter httpParameter;
      private HttpParameter[] httpParameters;
      private RestParameters restParameters;
      private GooglePicker picker;
      private Mode mode;
      private Mode[] modes;
   }
}
