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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Write and read back of the script properties of the viewsheet objects. Every property that has a setter is written
 * from a script with a value of its type (a number, a flag, a text, a colour, a point, a size, insets, a font, an
 * array) and read back; the read must give what was written. A property whose write is silently dropped or changed
 * would otherwise only show in a dashboard. The properties that cannot round-trip for a reason of their own (a value
 * from a fixed set, a value that needs data or other objects, a value that is clamped) are listed with the reason, so
 * that the list can only shrink.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScriptPropertyRoundTripTest {
   /** The assembly types; the scriptable of a time slider and a current selection have other names. */
   private static final String[] TYPES = {
      "Calendar", "CheckBox", "ComboBox", "Cylinder", "Gauge", "GroupContainer", "Image", "Line", "Oval",
      "RadioButton", "TimeSlider", "Rectangle", "CurrentSelection", "SelectionList", "SelectionTree", "Slider",
      "Spinner", "Submit", "Tab", "Table", "TextInput", "Text", "Thermometer", "SlidingScale", "Chart", "CalcTable",
      "Crosstab" };

   /** Properties that do not take the value of their type, with the reason. */
   private static final Map<String, String> KNOWN = new TreeMap<>();

   static {
      for(String t : new String[] { "CheckBox", "ComboBox", "RadioButton", "TextInput", "Slider", "Spinner" }) {
         KNOWN.put(t + ".labelPosition", "a position name from a fixed set");
      }

      for(String t : new String[] { "CheckBox", "ComboBox", "RadioButton" }) {
         KNOWN.put(t + ".datatype", "a type name from a fixed set");
         KNOWN.put(t + ".labels", "labels need the values of an embedded list");
         KNOWN.put(t + ".values", "the values are read back only for an embedded list");
      }

      for(String t : new String[] { "Image", "Text" }) {
         KNOWN.put(t + ".popLocation", "a location name from a fixed set");
      }

      KNOWN.put("Image.imageAlpha", "a number as text");
      KNOWN.put("Tab.selectedIndex", "an index of an existing child");
      KNOWN.put("Tab.selectedObject", "the name of an existing child");
      KNOWN.put("Table.tipView", "the name of an existing assembly");
      KNOWN.put("TimeSlider.rangeType", "a type from a fixed set");
      KNOWN.put("TimeSlider.rangeSize", "depends on the range type");
      KNOWN.put("TimeSlider.maxRangeSize", "depends on the range type");
      KNOWN.put("Chart.barCornerRadius", "clamped to 0.5");
      KNOWN.put("Chart.mapType", "a map type from a fixed set");
      KNOWN.put("Chart.webMapStyle", "a style name from a fixed set");
      // exposed on the epic only (Bug #75067 tooltipStyle, the tree chart layout)
      KNOWN.put("Chart.tooltipStyle", "a style name from a fixed set");
      KNOWN.put("Chart.treeLayout", "a layout name from a fixed set");
      KNOWN.put("Table.size", "clamped to the minimum size of the table");
      KNOWN.put("Thermometer.ranges", "the ranges are numbers");
      KNOWN.put("SlidingScale.ranges", "the ranges are numbers");
      KNOWN.put("Cylinder.ranges", "read as the range list of the gauge, not the written array");
      KNOWN.put("Gauge.ranges", "read as the range list of the gauge, not the written array");
      KNOWN.put("Calendar.selectedObjects", "only dates are accepted");
      KNOWN.put("SelectionList.selectedObjects", "only values of the list can be selected");
      KNOWN.put("SelectionTree.selectedObjects", "only values of the tree can be selected");
   }

   private static String ruleOf(String type, String key) {
      String exact = KNOWN.get(type + "." + key);

      if(exact != null) {
         return exact;
      }

      // the single-field properties of a gauge, a calendar and a selection list read back the first field only
      if(key.equals("fields")) {
         return "read as the first field only";
      }

      return null;
   }

   private static Object[] script(Class<?> type, Object before) {
      if(type == boolean.class || type == Boolean.class) {
         boolean flipped = !(before instanceof Boolean b && b);
         return new Object[] { String.valueOf(flipped), flipped };
      }

      if(type == int.class || type == Integer.class || type == double.class || type == Double.class ||
         type == float.class || type == long.class || type == short.class)
      {
         double current = before instanceof Number n ? n.doubleValue() : 0;
         int value = current == 7 ? 8 : 7;
         return new Object[] { String.valueOf(value), value };
      }

      if(type == String.class) {
         String text = before == null ? null : before.toString();

         if("true".equals(text) || "false".equals(text)) {
            boolean flipped = !Boolean.parseBoolean(text);
            return new Object[] { "'" + flipped + "'", String.valueOf(flipped) };
         }

         if(text != null && text.matches("-?[0-9.]+")) {
            int value = text.startsWith("7") ? 9 : 7;
            return new Object[] { String.valueOf(value), String.valueOf(value) };
         }

         return new Object[] { "'written'", "written" };
      }

      if(type == Color.class) {
         return new Object[] { "0x336699", new Color(0x336699) };
      }

      if(type == Point.class) {
         return new Object[] { "[12, 34]", new Point(12, 34) };
      }

      if(type == Dimension.class) {
         return new Object[] { "[12, 34]", new Dimension(12, 34) };
      }

      if(type == Insets.class) {
         return new Object[] { "[1, 2, 3, 4]", new Insets(1, 2, 3, 4) };
      }

      if(type == Font.class) {
         return new Object[] { "'Arial-bold-14'", new Font("Arial", Font.BOLD, 14) };
      }

      if(type == String[].class || type == Object[].class) {
         return new Object[] { "['a', 'b']", List.of("a", "b") };
      }

      return null;
   }

   private static boolean same(Object expected, Object read) {
      // a number kept as text may be read as 7.0
      if(expected instanceof String text && text.matches("-?[0-9.]+") && read != null &&
         read.toString().matches("-?[0-9.]+"))
      {
         return Double.parseDouble(read.toString()) == Double.parseDouble(text);
      }

      if(expected instanceof Number n) {
         return read instanceof Number r && r.doubleValue() == n.doubleValue();
      }

      if(expected instanceof List<?> list) {
         return read instanceof Object[] array && Arrays.asList(array).equals(list);
      }

      if(expected instanceof Font font) {
         return read instanceof Font f && f.getFamily().equals(font.getFamily()) && f.getStyle() == font.getStyle() &&
            f.getSize() == font.getSize();
      }

      return Objects.equals(expected, read) || (read != null && expected != null && read.toString().equals(expected.toString()));
   }

   @Test
   void everyPropertyReadsBackWhatWasWritten() throws Exception {
      List<String> broken = new ArrayList<>();
      Set<String> exercisedKnown = new TreeSet<>();
      int tested = 0;
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());

      try {
         for(String type : TYPES) {
            Viewsheet vs = new Viewsheet();
            vs.getVSAssemblyInfo().setName("vs1");
            Class<?> assemblyClass = Class.forName("inetsoft.uql.viewsheet." + type + "VSAssembly");
            VSAssembly assembly;

            try {
               Constructor<?> ctor = assemblyClass.getConstructor(Viewsheet.class, String.class);
               assembly = (VSAssembly) ctor.newInstance(vs, type + "1");
            }
            catch(NoSuchMethodException e) {
               assembly = (VSAssembly) assemblyClass.getConstructor().newInstance();
               assembly.getVSAssemblyInfo().setName(type + "1");
            }

            vs.addAssembly(assembly);
            ViewsheetSandbox box = mock(ViewsheetSandbox.class);
            when(box.getID()).thenReturn("vs1");
            when(box.isRuntime()).thenReturn(true);
            when(box.getViewsheet()).thenReturn(vs);
            when(box.getAssetEntry()).thenReturn(AssetEntry.createAssetEntry("1^128^__NULL__^roundtrip"));

            String scriptable = type.equals("TimeSlider") ? "RangeSlider" :
               type.equals("CurrentSelection") ? "SelectionContainer" : type;
            VSAScriptable s = (VSAScriptable) Class.forName("inetsoft.report.script.viewsheet." + scriptable +
                                                            "VSAScriptable")
               .getConstructor(ViewsheetSandbox.class).newInstance(box);
            s.setAssembly(type + "1");
            Set<Object> publicKeys = new HashSet<>(Arrays.asList(s.getMemberKeys()));

            Field field = VSAScriptable.class.getDeclaredField("propmap");
            field.setAccessible(true);
            Map<String, Object> properties = (Map<String, Object>) field.get(s);
            engine.put("s", s);

            for(String key : new TreeSet<>(properties.keySet())) {
               // the dotted names are the old aliases of the camel case names and are not listed to the script
               if(!publicKeys.contains(key) || !(properties.get(key) instanceof PropertyDescriptor descriptor)) {
                  continue;
               }

               // a read-only property has no setter
               Field setter = PropertyDescriptor.class.getDeclaredField("setter");
               setter.setAccessible(true);

               if(setter.get(descriptor) == null) {
                  continue;
               }

               Object before = s.getMember(key);
               Object[] value = script(descriptor.getType(), before);

               if(value == null) {
                  continue;
               }

               String name = type + "." + key;
               tested++;
               engine.put("s", s);
               try {
                  engine.exec(engine.compile("s." + key + " = " + value[0] + ";"), null, null);
               }
               catch(Exception ex) {
                  throw new IllegalStateException(name + " = " + value[0] + " type " + descriptor.getType(), ex);
               }
               Object read = s.getMember(key);

               if(same(value[1], read)) {
                  continue;
               }

               if(ruleOf(type, key) != null) {
                  exercisedKnown.add(name);
               }
               else {
                  broken.add(name + ": wrote " + value[0] + ", read " + read + " (was " + before + ")");
               }
            }
         }
      }
      finally {
         engine.close();
      }

      assertTrue(tested > 400, "the properties that were written: " + tested);
      assertEquals(List.of(), broken);

      // a listed property that now round-trips is removed from the list
      List<String> stale = new ArrayList<>();

      for(String name : KNOWN.keySet()) {
         if(!exercisedKnown.contains(name) && TYPES.length > 0) {
            stale.add(name);
         }
      }

      assertEquals(List.of(), stale, "listed properties that round-trip now (remove them from the list)");
   }
}
