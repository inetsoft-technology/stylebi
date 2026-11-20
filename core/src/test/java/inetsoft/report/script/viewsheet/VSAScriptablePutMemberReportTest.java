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

package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.util.Arrays;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81633: a failed property assignment must reach the script author when the product has
 * a clear error text. Bug #81922: that text shows the value and the expected type in words a
 * script author reads, never a Java class name or an identity hash. Bug #81923: a null
 * position/size or a non-numeric imageAlpha gives such a text instead of a silent NPE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSAScriptablePutMemberReportTest {
   private Viewsheet viewsheet;
   private ViewsheetSandbox box;
   private TextVSAScriptable scriptable;

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);
      when(box.isRuntime()).thenReturn(true);

      scriptable = add(new TextVSAssembly(), "Text1", TextVSAScriptable::new);
      Tool.getUserMessage();
   }

   private <T extends VSAScriptable> T add(VSAssembly assembly, String name,
                                           Function<ViewsheetSandbox, T> factory)
   {
      assembly.getVSAssemblyInfo().setName(name);
      viewsheet.addAssembly(assembly);
      T result = factory.apply(box);
      result.setAssembly(name);
      return result;
   }

   private static String warning() {
      UserMessage msg = Tool.getUserMessage();
      assertNotNull(msg, "expected a warning for the script author");
      return msg.getMessage();
   }

   /** The author text must not contain Java internals: class names or identity hashes. */
   private static void assertReadable(String text) {
      assertFalse(text.contains("@"), text);
      assertFalse(text.contains("java."), text);
      assertFalse(text.contains("class "), text);
      assertFalse(text.contains("[L"), text);
   }

   @Test
   void invalidPopLocationReachesAuthor() {
      scriptable.putMember("popLocation", "x");

      UserMessage msg = Tool.getUserMessage();
      assertNotNull(msg);
      assertTrue(msg.getMessage().contains("popLocation"));
      assertTrue(msg.getMessage().contains("Invalid PopLocation"));
   }

   // Bug #81922: Text1.size = [100] (a JS array reaches the converter as a host Object[])
   @Test
   void shortSizeArrayShowsValueAndExpectedSize() {
      scriptable.putMember("size", new Object[] { 100.0 });

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("'size'"), text);
      assertTrue(text.contains("[100]"), text);
      assertTrue(text.contains("width") && text.contains("height"), text);
   }

   // Bug #81922: ComboBox1.minDate = '2020-01-01' (the property expects a Date)
   @Test
   void minDateTextShowsValueAndExpectedDate() {
      ComboBoxVSAScriptable combo = add(new ComboBoxVSAssembly(), "ComboBox1",
                                        ComboBoxVSAScriptable::new);
      combo.putMember("minDate", "2020-01-01");

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("'minDate'"), text);
      assertTrue(text.contains("'2020-01-01'"), text);
      assertTrue(text.contains("date"), text);
   }

   // Bug #81922: Text1.size = Text2 (a script object, whose toString has an identity hash)
   @Test
   void scriptObjectValueShowsNoIdentityHash() {
      TextVSAScriptable text2 = add(new TextVSAssembly(), "Text2", TextVSAScriptable::new);
      scriptable.putMember("size", text2);

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("Text2"), text);

      // and as an array element
      scriptable.putMember("size", new Object[] { text2 });
      text = warning();
      assertReadable(text);
      assertTrue(text.contains("[Text2]"), text);
   }

   // Bug #81922: other conversion failures (Insets, int, nested array, border colors)
   @Test
   void otherConversionFailuresAreReadable() {
      scriptable.putMember("borders", new Object[] { 1.0, 2.0 });
      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("[1, 2]"), text);

      scriptable.putMember("alpha", "abc");
      text = warning();
      assertReadable(text);
      assertTrue(text.contains("'abc'"), text);
      assertTrue(text.contains("integer"), text);

      scriptable.putMember("size", new Object[] { new Object[] { 100.0, 50.0 } });
      text = warning();
      assertReadable(text);
      assertTrue(text.contains("[[100, 50]]"), text);

      scriptable.putMember("borderColors", new Object[] { "red", "nope" });
      text = warning();
      assertReadable(text);
      assertTrue(text.contains("'nope'"), text);
   }

   // Bug #81922: a huge array is shortened in the text
   @Test
   void longArrayIsShortened() {
      ComboBoxVSAScriptable combo = add(new ComboBoxVSAssembly(), "ComboBox1",
                                        ComboBoxVSAScriptable::new);
      Object[] values = new Object[10000];
      Arrays.fill(values, "x");
      combo.putMember("minDate", values);

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("..."), text);
      assertTrue(text.length() < 500, text);
   }

   // valid values still apply without any warning
   @Test
   void validSizeAndPositionGiveNoWarning() {
      scriptable.putMember("size", new Object[] { 100.0, 50.0 });
      scriptable.putMember("position", new Object[] { 5.0, 6.0 });

      assertNull(Tool.getUserMessage());
      assertEquals(new Dimension(100, 50), scriptable.getSize());
      assertEquals(new Point(5, 6), scriptable.getPosition());
   }

   // Bug #81922 control: the string form "100,50" still sets the size without a warning
   @Test
   void stringSizeStillAppliesWithoutWarning() {
      scriptable.putMember("size", "100,50");

      assertNull(Tool.getUserMessage());
      assertEquals(new Dimension(100, 50), scriptable.getSize());
   }

   // Bug #81923: Text1.position = null (replaces #81633's rawNullPointerStaysInLogOnly, which
   // pinned the silent NPE of this input as its example of a raw JDK failure)
   @Test
   void nullPositionReachesAuthor() {
      scriptable.putMember("position", new Object[] { 5.0, 6.0 });
      scriptable.putMember("position", null);

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("'position'"), text);
      assertTrue(text.contains("null"), text);
      assertEquals(new Point(5, 6), scriptable.getPosition());
   }

   // Bug #81923: size = null on the base and on the setSize overrides that read the size first
   @ParameterizedTest
   @ValueSource(strings = { "Text", "Line", "CalcTable", "SelectionList" })
   void nullSizeReachesAuthor(String kind) {
      VSAScriptable target = switch(kind) {
         case "Line" -> add(new LineVSAssembly(), "Line1", LineVSAScriptable::new);
         case "CalcTable" -> add(new CalcTableVSAssembly(), "CalcTable1",
                                 CalcTableVSAScriptable::new);
         case "SelectionList" -> add(new SelectionListVSAssembly(), "SelectionList1",
                                     SelectionListVSAScriptable::new);
         default -> scriptable;
      };
      Dimension before = target.getSize();
      target.putMember("size", null);

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("'size'"), text);
      assertTrue(text.contains("null"), text);
      assertEquals(before, target.getSize());
   }

   // Bug #81923: Image1.imageAlpha = 'abc' / '' / null
   @Test
   void nonNumericImageAlphaReachesAuthor() {
      ImageVSAScriptable image = add(new ImageVSAssembly(), "Image1", ImageVSAScriptable::new);
      image.putMember("imageAlpha", "50");
      assertNull(Tool.getUserMessage());

      for(String value : new String[] { "abc", "", null }) {
         image.putMember("imageAlpha", value);

         String text = warning();
         assertReadable(text);
         assertTrue(text.contains("'imageAlpha'"), text);
         assertTrue(text.contains("Numeric value expected"), text);
         assertTrue(text.contains(value == null ? "null" : "'" + value + "'"), text);
         assertEquals("50", image.getMember("imageAlpha"));
      }
   }

   // Bug #81923: Group1.imageAlpha = 'abc'
   @Test
   void nonNumericGroupImageAlphaReachesAuthor() {
      GroupContainerVSAScriptable group = add(new GroupContainerVSAssembly(), "Group1",
                                              GroupContainerVSAScriptable::new);
      group.putMember("imageAlpha", "abc");

      String text = warning();
      assertReadable(text);
      assertTrue(text.contains("Numeric value expected: 'abc'"), text);
   }
}
