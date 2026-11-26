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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.util.DataSpace;
import inetsoft.util.FileSystemService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.dialog.ImagePreviewPaneService;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class VSLayoutControllerServiceTest {

   @BeforeEach
   void setup() throws Exception {
      BinaryTransferService binaryTransferService = new BinaryTransferService(fileSystemService);
      ImagePreviewPaneService imagePreviewPaneService =
         new ImagePreviewPaneService(viewsheetService, objectService, binaryTransferService, fileSystemService, dataSpace);
      service = new VSLayoutControllerService(coreLifecycleService, viewsheetService,
                                              imagePreviewPaneService, objectModelService,
                                              vsLayoutService, vsObjectTreeService);
   }

   // Bug #16600 Make sure that when not setting a script, the default value doesnt error out.
   @Test
   void scriptIsSet() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);
      when(viewsheet.getUploadedImageNames()).thenReturn(new String[0]);

      PrintLayout layout = new PrintLayout();
      List<VSAssemblyLayout> layouts = new ArrayList<>();
      VSEditableAssemblyLayout assemblyLayout = new VSEditableAssemblyLayout();
      String assemblyLayoutName = "ImageLayout";
      assemblyLayout.setName(assemblyLayoutName);
      assemblyLayout.setInfo(new ImageVSAssemblyInfo());
      layouts.add(assemblyLayout);
      layout.setHeaderLayouts(layouts);

      when(layoutInfo.getPrintLayout()).thenReturn(layout);
      when(vsLayoutService.findAssemblyLayout(layout, assemblyLayoutName, 0))
         .thenReturn(Optional.of(assemblyLayout));

      ImagePropertyDialogModel result =
         service.getImagePropertyDialogModel("", 0, assemblyLayoutName, null);

      assertFalse(result.clickableScriptPaneModel().scriptEnabled());
      assertEquals("", result.clickableScriptPaneModel().scriptExpression());
      assertEquals("", result.clickableScriptPaneModel().onClickExpression());
   }

   /**
    * Bug 82400: the dialog's model-to-info mapping now lives in
    * {@link VSLayoutService#applyLayoutImageOptions}; the print-layout dialog's save must still
    * write exactly what it wrote before, including the skin image's reset-then-scale ordering.
    */
   @Test
   void savingTheImageDialogWritesThePictureAndScaleOptionsOntoTheLayoutEntry() throws Exception {
      when(viewsheetService.getViewsheet(any(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);

      PrintLayout layout = new PrintLayout();
      VSEditableAssemblyLayout assemblyLayout = new VSEditableAssemblyLayout();
      assemblyLayout.setName("ImageLayout");
      ImageVSAssemblyInfo info = new ImageVSAssemblyInfo();
      assemblyLayout.setInfo(info);
      layout.setHeaderLayouts(new ArrayList<>(List.of(assemblyLayout)));
      when(layoutInfo.getPrintLayout()).thenReturn(layout);
      when(vsLayoutService.findAssemblyLayout(layout, "ImageLayout", 0))
         .thenReturn(Optional.of(assemblyLayout));

      String skin = ImageVSAssemblyInfo.SKIN_IMAGE + ImageVSAssemblyInfo.SKIN_TITLE;
      service.setImagePropertyDialogModel("", "Print Layout", 0, "ImageLayout",
         dialogModel(skin, 50, true, true, true, 3, 4, 5, 6), null, commandDispatcher);

      assertEquals(skin, info.getImageValue());
      assertEquals("50", info.getImageAlphaValue());
      assertTrue(info.isScaleImageValue());
      assertTrue(info.isTileValue());
      // the scale block runs after the skin reset, so the dialog's aspect ratio wins
      assertTrue(info.isMaintainAspectRatioValue());
      assertEquals(new Insets(1, 1, 1, 1), info.getScale9Value());

      service.setImagePropertyDialogModel("", "Print Layout", 0, "ImageLayout",
         dialogModel(skin, 50, true, false, false, 3, 4, 5, 6), null, commandDispatcher);

      assertFalse(info.isMaintainAspectRatioValue());
      assertFalse(info.isTileValue());
      assertEquals(new Insets(3, 4, 5, 6), info.getScale9Value());
   }

   @Test
   void theSharedMappingLeavesThePictureAloneWithoutASelectedImageAndSkipsInsetsWhenKeepingRatio() {
      ImageVSAssemblyInfo info = new ImageVSAssemblyInfo();
      info.setImageValue("^UPLOADED^a.png");
      info.setImageAlphaValue("70");
      info.setScale9Value(new Insets(7, 7, 7, 7));

      VSLayoutService.applyLayoutImageOptions(info, null, 10, true, true, false,
                                              new Insets(1, 2, 3, 4));

      assertEquals("^UPLOADED^a.png", info.getImageValue());
      assertEquals("70", info.getImageAlphaValue(), "alpha is written only with a picture");
      assertEquals(new Insets(7, 7, 7, 7), info.getScale9Value(),
                   "insets apply only when the aspect ratio is not kept");
   }

   private static ImagePropertyDialogModel dialogModel(String image, int alpha, boolean scale,
                                                       boolean maintain, boolean tile, int top,
                                                       int left, int bottom, int right)
   {
      ImagePreviewPaneModel preview = ImagePreviewPaneModel.builder()
         .selectedImage(image).alpha(alpha).imageTree(TreeNodeModel.builder().build()).build();
      ImageScalePaneModel scalePane = ImageScalePaneModel.builder()
         .scaleImageChecked(scale).maintainAspectRatio(maintain).tile(tile)
         .top(top).left(left).bottom(bottom).right(right).build();

      return ImagePropertyDialogModel.builder()
         .imageGeneralPaneModel(ImageGeneralPaneModel.builder()
            .staticImagePaneModel(StaticImagePaneModel.builder()
               .imagePreviewPaneModel(preview).build()).build())
         .imageAdvancedPaneModel(ImageAdvancedPaneModel.builder()
            .imageScalePaneModel(scalePane).build())
         .build();
   }

   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSObjectService objectService;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock LayoutInfo layoutInfo;
   @Mock CommandDispatcher commandDispatcher;
   @Mock VSObjectModelFactoryService objectModelService;
   @Mock VSLayoutService vsLayoutService;
   @Mock VSObjectTreeService vsObjectTreeService;
   @Mock FileSystemService fileSystemService;
   @Mock DataSpace dataSpace;
   private VSLayoutControllerService service;
}
