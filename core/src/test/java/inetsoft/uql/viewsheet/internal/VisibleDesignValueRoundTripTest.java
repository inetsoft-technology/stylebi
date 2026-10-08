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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78039: the titleVisibleValue (TitleInfo), labelVisibleValue (LabelInfo)
 * and showTextValue/showBarValue (SelectionBaseVSAssemblyInfo) design values
 * must be saved as stored, not collapsed to the boolean the property dialog
 * shows. A $(var) or =expr binding, or a truthy literal such as yes, was
 * rewritten to "false" on every save.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VisibleDesignValueRoundTripTest {
   /**
    * Stored design values. null means the attribute is absent from the
    * imported XML; "" is read as absent too.
    */
   private static final String[] VALUES = {
      "true", "false", null, "", "yes", "$(v)", "=true", "=a < b && \"c\""
   };

   /**
    * case name, assembly factory, attribute, value written for a missing
    * attribute (the parse default), runtime getter.
    */
   private static final Object[][] ATTRS = {
      { "Table.titleVisibleValue", (Supplier<VSAssemblyInfo>) TableVSAssemblyInfo::new,
        "titleVisibleValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((TableVSAssemblyInfo) i).isTitleVisible() },
      { "SelectionList.titleVisibleValue", (Supplier<VSAssemblyInfo>) SelectionListVSAssemblyInfo::new,
        "titleVisibleValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((SelectionListVSAssemblyInfo) i).isTitleVisible() },
      { "TextInput.labelVisibleValue", (Supplier<VSAssemblyInfo>) TextInputVSAssemblyInfo::new,
        "labelVisibleValue", "false",
        (Predicate<VSAssemblyInfo>) i -> ((TextInputVSAssemblyInfo) i).getLabelInfo().isLabelVisible() },
      { "SelectionList.showTextValue", (Supplier<VSAssemblyInfo>) SelectionListVSAssemblyInfo::new,
        "showTextValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((SelectionListVSAssemblyInfo) i).isShowText() },
      { "SelectionList.showBarValue", (Supplier<VSAssemblyInfo>) SelectionListVSAssemblyInfo::new,
        "showBarValue", "false",
        (Predicate<VSAssemblyInfo>) i -> ((SelectionListVSAssemblyInfo) i).isShowBar() },
      // the other assemblies that save the same TitleInfo, LabelInfo and
      // SelectionBaseVSAssemblyInfo attributes through their own writeContents
      { "Crosstab.titleVisibleValue", (Supplier<VSAssemblyInfo>) CrosstabVSAssemblyInfo::new,
        "titleVisibleValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((CrosstabVSAssemblyInfo) i).isTitleVisible() },
      { "Chart.titleVisibleValue", (Supplier<VSAssemblyInfo>) ChartVSAssemblyInfo::new,
        "titleVisibleValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((ChartVSAssemblyInfo) i).isTitleVisible() },
      { "CheckBox.titleVisibleValue", (Supplier<VSAssemblyInfo>) CheckBoxVSAssemblyInfo::new,
        "titleVisibleValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((CheckBoxVSAssemblyInfo) i).isTitleVisible() },
      { "SelectionTree.showTextValue", (Supplier<VSAssemblyInfo>) SelectionTreeVSAssemblyInfo::new,
        "showTextValue", "true",
        (Predicate<VSAssemblyInfo>) i -> ((SelectionTreeVSAssemblyInfo) i).isShowText() },
      { "SelectionTree.showBarValue", (Supplier<VSAssemblyInfo>) SelectionTreeVSAssemblyInfo::new,
        "showBarValue", "false",
        (Predicate<VSAssemblyInfo>) i -> ((SelectionTreeVSAssemblyInfo) i).isShowBar() },
   };

   static Stream<Arguments> cases() {
      return Stream.of(ATTRS).flatMap(attr -> Stream.of(VALUES)
         .map(value -> Arguments.of(attr[0], attr[1], attr[2], attr[3], attr[4], value)));
   }

   @ParameterizedTest(name = "{0}={5}")
   @MethodSource("cases")
   void designValueSurvivesTwoSaves(String name, Supplier<VSAssemblyInfo> factory,
                                    String attr, String defaultValue,
                                    Predicate<VSAssemblyInfo> runtime, String value)
      throws Exception
   {
      VSAssemblyInfo loaded = load(factory, attr, value);
      String saved = write(loaded);
      // parse treats an empty attribute as a missing one
      boolean missing = value == null || value.isEmpty();
      String expected = missing ? defaultValue : value;

      assertEquals(expected, attribute(parse(saved), attr), saved);

      if(missing || "true".equals(value) || "false".equals(value)) {
         // GUI-authored, empty and missing values are written exactly as before
         assertTrue(saved.contains(" " + attr + "=\"" + expected + "\""), saved);
      }

      VSAssemblyInfo reloaded = factory.get();
      reloaded.parseXML(parse(saved));
      assertEquals(runtime.test(loaded), runtime.test(reloaded),
                   name + ": the runtime value must not change across a save");

      String savedAgain = write(reloaded);
      assertEquals(saved, savedAgain, "a second save must be stable");

      VSAssemblyInfo reloadedAgain = factory.get();
      reloadedAgain.parseXML(parse(savedAgain));
      assertEquals(runtime.test(loaded), runtime.test(reloadedAgain),
                   name + ": the runtime value must not change across a second save");
   }

   /**
    * labelVisible is the only one of these values the sandbox evaluates (it is
    * in LabelInfo.getViewDynamicValues()), so its binding must be saved as the
    * binding even after it was evaluated.
    */
   @ParameterizedTest(name = "labelVisibleValue={0}")
   @MethodSource("labelBindings")
   void evaluatedLabelBindingIsSavedAsTheBinding(String binding) throws Exception {
      Supplier<VSAssemblyInfo> factory = TextInputVSAssemblyInfo::new;
      TextInputVSAssemblyInfo info =
         (TextInputVSAssemblyInfo) load(factory, "labelVisibleValue", binding);
      evaluate(info, binding);
      assertTrue(info.getLabelInfo().isLabelVisible(), "the evaluated binding is true");

      Element saved = parse(write(info));
      assertEquals(binding, attribute(saved, "labelVisibleValue"),
                   "the binding, not the runtime value, must be saved");

      TextInputVSAssemblyInfo reloaded = new TextInputVSAssemblyInfo();
      reloaded.parseXML(saved);
      evaluate(reloaded, binding);
      assertTrue(reloaded.getLabelInfo().isLabelVisible(),
                 "the reloaded binding must still evaluate to true");
   }

   static Stream<String> labelBindings() {
      return Stream.of("$(v)", "=true");
   }

   /**
    * Evaluate the binding the way ViewsheetSandbox.executeDynamicValues does.
    */
   private static void evaluate(InputVSAssemblyInfo info, String binding) {
      int evaluated = 0;

      for(DynamicValue value : info.getViewDynamicValues(true)) {
         if(binding.equals(value.getDValue())) {
            value.setRValue(Boolean.TRUE);
            evaluated++;
         }
      }

      assertEquals(1, evaluated, "the assembly must carry the binding");
   }

   /**
    * Load an assembly whose saved attribute is the given value (or is missing),
    * the way an imported or hand-edited asset reaches the parser.
    */
   private static VSAssemblyInfo load(Supplier<VSAssemblyInfo> factory, String attr,
                                      String value)
      throws Exception
   {
      String xml = write(factory.get());
      Matcher matcher = Pattern.compile(" " + attr + "=\"[^\"]*\"").matcher(xml);
      assertTrue(matcher.find(), xml);
      int start = matcher.start();
      int end = matcher.end();
      assertFalse(matcher.find(), attr + " must be written once\n" + xml);
      xml = xml.substring(0, start) +
         (value == null ? "" : " " + attr + "=\"" + Tool.escape(value) + "\"") +
         xml.substring(end);

      VSAssemblyInfo info = factory.get();
      info.parseXML(parse(xml));

      return info;
   }

   /**
    * The attribute of the (only) element that carries it, e.g. the nested
    * titleInfo or labelInfo element.
    */
   private static String attribute(Element root, String attr) {
      NodeList nodes = root.getElementsByTagName("*");
      String found = root.hasAttribute(attr) ? root.getAttribute(attr) : null;
      int count = found == null ? 0 : 1;

      for(int i = 0; i < nodes.getLength(); i++) {
         Element elem = (Element) nodes.item(i);

         if(elem.hasAttribute(attr)) {
            found = elem.getAttribute(attr);
            count++;
         }
      }

      assertEquals(1, count, attr + " must be written once");
      return found;
   }

   private static String write(VSAssemblyInfo info) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         info.writeXML(writer);
      }

      return buffer.toString();
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml)).getDocumentElement();
   }
}
