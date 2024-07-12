/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.graph.model.dialog;

import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.AxisDescriptor;
import inetsoft.uql.viewsheet.graph.CompositeTextFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@SreeHome()
class AxisPropertyDialogModelTest {
   @BeforeEach
   void setup() {
      axisPropertyDialogModel = new AxisPropertyDialogModel();
      axisPropertyDialogModel.setLinear(true);

      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      axisPropertyDialogModel.setAliasPaneModel(aliasPaneModel);

      AxisLabelPaneModel axisLabelPaneModel = new AxisLabelPaneModel();
      axisPropertyDialogModel.setAxisLabelPaneModel(axisLabelPaneModel);

      RotationRadioGroupModel rotationRadioGroupModel = new RotationRadioGroupModel();
      rotationRadioGroupModel.setRotation("0");
      axisLabelPaneModel.setRotationRadioGroupModel(rotationRadioGroupModel);

      AxisLinePaneModel axisLinePaneModel = new AxisLinePaneModel();
      axisPropertyDialogModel.setAxisLinePaneModel(axisLinePaneModel);
   }

   @Test
   void canHaveNullIncrements() throws Exception {
      AxisDescriptor axisDescriptor = new AxisDescriptor();
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, "", "", false);

      assertNull(axisDescriptor.getMinimum());
      assertNull(axisDescriptor.getMaximum());
      assertNull(axisDescriptor.getIncrement());
      assertNull(axisDescriptor.getMinorIncrement());
   }

   @Test
   void getCorrectFormat() throws Exception {
      AxisDescriptor axisDescriptor = spy(new AxisDescriptor());
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, null, "", false);
      verify(axisDescriptor, times(2)).getAxisLabelTextFormat();

      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, "column1", "", false);
      verify(axisDescriptor, times(2)).getColumnLabelTextFormat("column1");
   }

   @Test
   void getCorrectRotationFormat() throws Exception {
      String columnName = "column1";
      AxisDescriptor axisDescriptor = new AxisDescriptor();
      CompositeTextFormat cfmt = new CompositeTextFormat();
      cfmt.getUserDefinedFormat().setRotation(45.0);
      axisDescriptor.setColumnLabelTextFormat(columnName, cfmt);

      axisPropertyDialogModel.getAxisLabelPaneModel().getRotationRadioGroupModel().setRotation("auto");
      axisPropertyDialogModel.updateAxisPropertyDialogModel(axisDescriptor, columnName, "", false);
      assertNull(axisDescriptor.getColumnLabelTextFormat(columnName).getUserDefinedFormat().getRotation());
   }

   private AxisPropertyDialogModel axisPropertyDialogModel;
}
