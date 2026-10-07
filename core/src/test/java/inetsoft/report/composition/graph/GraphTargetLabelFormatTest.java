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
package inetsoft.report.composition.graph;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.form.TargetForm;
import inetsoft.graph.visual.LabelFormVO;
import inetsoft.test.*;
import inetsoft.uql.XFormatInfo;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77805: a target label pattern that can't be parsed or applied must not fail
 * the whole chart. The target is labeled with the plain value instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphTargetLabelFormatTest {
   static Stream<Arguments> badLabels() {
      String[] labels = {
         "{0,choice,}",                  // choice with no limits
         "{0,choice,abc}",               // choice with no limit separator
         "{0,choice,0#{ }n}",            // choice with an unparsable sub-pattern
         "{0,choice,0#a|1#{1,choice,}}", // nested malformed choice
         "{1,number}",                   // well-formed, but {1} is a String
         "{1,choice,0#a|1#b}",           // well-formed, but {1} is a String
         "{0,choice"                     // unmatched braces, fails at construction
      };
      return combine(labels);
   }

   static Stream<Arguments> goodLabels() {
      return combine(new String[] { "{0}", "{0} units", "{0,choice,0#low|2#high}" });
   }

   private static Stream<Arguments> combine(String[] labels) {
      List<Arguments> list = new ArrayList<>();

      for(String strategy : new String[] { "line", "statistic" }) {
         for(boolean valueFormat : new boolean[] { false, true }) {
            for(String label : labels) {
               list.add(Arguments.of(strategy, valueFormat, label));
            }
         }
      }

      return list.stream();
   }

   @ParameterizedTest(name = "{0} valueFormat={1} label={2}")
   @MethodSource("badLabels")
   void badLabelFallsBackToPlainValue(String strategy, boolean valueFormat, String label) {
      Tool.clearUserMessage();
      List<String> labels = plot(strategy, valueFormat, label);
      assertEquals(List.of(valueFormat ? "2.00" : "2"), labels);

      UserMessage msg = Tool.getUserMessage();
      assertNotNull(msg, "the author should be told the label failed");
      // the pattern is shown as MessageFormat normalizes it, e.g. 0# becomes 0.0#
      String argument = label.substring(0, label.indexOf(',') + 1);
      assertTrue(msg.getMessage().contains("target label \"" + argument),
                 msg.getMessage());
   }

   @ParameterizedTest(name = "{0} valueFormat={1} label={2}")
   @MethodSource("goodLabels")
   void validLabelIsApplied(String strategy, boolean valueFormat, String label) {
      Tool.clearUserMessage();
      List<String> labels = plot(strategy, valueFormat, label);
      assertNull(Tool.getUserMessage());
      String value = valueFormat ? "2.00" : "2";
      String expected = switch(label) {
         case "{0}" -> value;
         case "{0} units" -> value + " units";
         // only renders on the #53395 retry when there is a value format
         default -> "high";
      };

      assertEquals(List.of(expected), labels);
   }

   private static List<String> plot(String strategy, boolean valueFormat, String label) {
      Supplier<TargetStrategyWrapper> wrapper = "line".equals(strategy) ?
         () -> new DynamicLineWrapper("2") : () -> new PercentileWrapper("50");
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "x", "y" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
      GraphTarget target = new GraphTarget();
      target.setField("y");
      target.setStrategy(wrapper.get());
      target.setLabelFormats(label);

      if(valueFormat) {
         // GraphGenerator.addTarget sets the measure's default format the same way
         target.getTextFormat().getDefaultFormat()
            .setFormat(new XFormatInfo("DecimalFormat", "#,##0.00"));
      }

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("x", "y"));
      TargetForm form = new TargetForm();
      target.initializeForm(form, data);
      graph.addForm(form);
      VGraph vgraph = assertDoesNotThrow(
         () -> Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 400, 300));

      List<String> labels = new ArrayList<>();
      collectLabels(vgraph, labels);
      return labels;
   }

   private static void collectLabels(Visualizable visual, List<String> labels) {
      if(visual instanceof LabelFormVO) {
         labels.add(((LabelFormVO) visual).getLabel());
      }

      if(visual instanceof VContainer) {
         VContainer container = (VContainer) visual;

         for(int i = 0; i < container.getVisualCount(); i++) {
            collectLabels(container.getVisual(i), labels);
         }
      }
   }
}
