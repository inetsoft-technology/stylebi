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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
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

   @Test
   public void symbolRoundCornersIsWrittenToEditedLegendOnly() {
      LegendFormatDialogModel dialog = new LegendFormatDialogModel();
      LegendFormatGeneralPaneModel pane = new LegendFormatGeneralPaneModel();
      pane.setPosition("");
      pane.setFillColor("#ffffff");
      pane.setSymbolRoundCorners(false);
      dialog.setLegendFormatGeneralPaneModel(pane);
      dialog.setLegendScalePaneModel(new LegendScalePaneModel());
      AliasPaneModel aliasPaneModel = new AliasPaneModel();
      aliasPaneModel.setAliasList(new ModelAlias[0]);
      dialog.setAliasPaneModel(aliasPaneModel);

      LegendsDescriptor legendsDesc = new LegendsDescriptor();
      LegendDescriptor edited = new LegendDescriptor();
      LegendDescriptor unrelated = new LegendDescriptor();
      legendsDesc.setColorLegendDescriptor(edited);
      legendsDesc.setSizeLegendDescriptor(unrelated);

      dialog.updateLegendFormatDialogModel(new VSChartInfo(), legendsDesc, edited, null);

      assertFalse(edited.isSymbolRoundCorners(),
                  "the edited legend should reflect the dialog value");
      assertTrue(unrelated.isSymbolRoundCorners(),
                 "another legend on the same chart must not be affected");
   }

   @Test
   public void equalsContentDiscriminatesSymbolRoundCorners() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      a.setSymbolRoundCorners(true);
      b.setSymbolRoundCorners(false);

      assertFalse(a.equalsContent(b));

      b.setSymbolRoundCorners(true);

      assertTrue(a.equalsContent(b));
   }
}
