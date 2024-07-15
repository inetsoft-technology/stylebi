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
package inetsoft.web.graph.model.dialog;

import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome()
@ExtendWith(MockitoExtension.class)
public class LegendFormatDialogModelTest {
   @Test
   public void hasCorrectBorderColor() throws Exception {
      LegendFormatDialogModel legendFormatDialogModel = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel legendFormatGeneralPaneModel = new LegendFormatGeneralPaneModel();
      legendFormatGeneralPaneModel.setPosition("");
      legendFormatDialogModel.setLegendFormatGeneralPaneModel(legendFormatGeneralPaneModel);
      LegendScalePaneModel legendScalePaneModel = new LegendScalePaneModel();
      legendFormatDialogModel.setLegendScalePaneModel(legendScalePaneModel);
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      legendFormatDialogModel.setAliasPaneModel(aliasPaneModel);

      LegendDescriptor legendDescriptor = new LegendDescriptor();
      LegendsDescriptor legendsDescriptor = new LegendsDescriptor();
      legendFormatGeneralPaneModel.setFillColor("#ffffff");
      legendFormatDialogModel.updateLegendFormatDialogModel(new VSChartInfo(), legendsDescriptor,
                                                            legendDescriptor, null);

      assertEquals(Color.WHITE, legendsDescriptor.getBorderColor());
   }
}
