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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import inetsoft.test.SreeHome;
import inetsoft.web.viewsheet.model.annotation.VSAnnotationModel;
import inetsoft.web.viewsheet.model.calendar.VSCalendarModel;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.model.table.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SreeHome()
class VSObjectModelTest {
   @Test
   void checkSubTypes() throws Exception {
      // Get the list of JsonSubType.Type's in VSObjectModel
      final List<JsonSubTypes.Type> types =
         Arrays.stream(VSObjectModel.class.getAnnotation(JsonSubTypes.class).value())
               .collect(Collectors.toList());

      // Check that VSEmbeddedTable maps to VSTable and that the name property is the
      // prefix of the class' name in every other case
      types.forEach(type -> {
         final Class clazz = type.value();
         final String objectType = type.name();

         if(type.value() == VSEmbeddedTableModel.class) {
            assertTrue(type.name().equals("VSTable"));
         }
         else {
            assertTrue(clazz.getSimpleName().startsWith(objectType));
         }
      });

      final List<Class> subTypes = types.stream()
                                        .map(JsonSubTypes.Type::value)
                                        .collect(Collectors.toList());

      // Assert that the sizes of both lists match
      if(subclasses.size() > subTypes.size()) {
         subclasses.removeAll(subTypes);
         System.err.println("JsonSubTypes.Type's is missing the following: ");

         for(Class subclass : subclasses) {
            System.err.println(subclass);
         }

         fail();
      }
      else if(subclasses.size() < subTypes.size()) {
         fail("JsonSubTypes.Type's has duplicate entries");
      }

      // Check that all subclasses of VSObjectModel are represented in the
      // JsonSubTypes annotation
      for(Class clazz : subclasses) {
         assertTrue(subTypes.contains(clazz));
      }
   }

   private static final Set<Class> subclasses = new HashSet<>(Arrays.asList(
      VSAnnotationModel.class,
      VSCalcTableModel.class,
      VSCalendarModel.class,
      VSChartModel.class,
      VSCheckBoxModel.class,
      VSComboBoxModel.class,
      VSCrosstabModel.class,
      VSCylinderModel.class,
      VSEmbeddedTableModel.class,
      VSGaugeModel.class,
      VSGroupContainerModel.class,
      VSImageModel.class,
      VSLineModel.class,
      VSOvalModel.class,
      VSPageBreakModel.class,
      VSRadioButtonModel.class,
      VSRangeSliderModel.class,
      VSRectangleModel.class,
      VSSelectionListModel.class,
      VSSelectionTreeModel.class,
      VSSliderModel.class,
      VSSlidingScaleModel.class,
      VSSpinnerModel.class,
      VSSubmitModel.class,
      VSTabModel.class,
      VSTableModel.class,
      VSTextInputModel.class,
      VSTextModel.class,
      VSThermometerModel.class,
      VSViewsheetModel.class,
      VSSelectionContainerModel.class
   ));
}
