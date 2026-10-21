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
package inetsoft.web.vswizard.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.vswizard.controller.VSWizardObjectToolbarService.FullEditorAssembly;
import inetsoft.web.vswizard.model.VSWizardOriginalModel;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.io.*;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Bug #78734: Full Editor clicked in the Visualization Recommender before the preview assembly
 * reached the client sends no assembly name. The service must then resolve the latest preview
 * assembly itself, copy the size of the original assembly onto it and answer which assembly the
 * full editor opens.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
// 8-17 s on a developer machine, almost all of it the Spring context, so not "core"
@Tag("slow")
class VSWizardObjectToolbarServiceTest {
   private static final String ID = "rvs-78734";
   private static final String ORIGINAL = "Chart1";
   private static final String OLDER_TEMP = "Recommender__1700000000000";
   private static final String TEMP = "Recommender__1700000005000";
   private static final Dimension ORIGINAL_SIZE = new Dimension(400, 300);
   private static final Dimension PREVIEW_SIZE = new Dimension(1007, 784);

   @BeforeEach
   void setUp() throws Exception {
      vs = new Viewsheet();
      ChartVSAssembly original = new ChartVSAssembly(vs, ORIGINAL);
      original.setPixelSize(ORIGINAL_SIZE);
      vs.addAssembly(original);

      viewsheetService = mock(ViewsheetService.class);
      rvs = mock(RuntimeViewsheet.class);
      box = mock(ViewsheetSandbox.class);
      temporaryInfoService = mock(VSWizardTemporaryInfoService.class);
      VSTemporaryInfo temporaryInfo = mock(VSTemporaryInfo.class);
      VSWizardOriginalModel originalModel = new VSWizardOriginalModel();
      originalModel.setOriginalName(ORIGINAL);

      when(viewsheetService.getViewsheet(ID, principal)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(temporaryInfoService.getVSTemporaryInfo(rvs)).thenReturn(temporaryInfo);
      when(temporaryInfo.getOriginalModel()).thenReturn(originalModel);

      service = new VSWizardObjectToolbarService(temporaryInfoService, viewsheetService);
   }

   @Test
   void withNameTheNamedAssemblyTakesTheOriginalSize() throws Exception {
      ChartVSAssembly temp = addTemp(new ChartVSAssembly(vs, TEMP));

      FullEditorAssembly result = service.gotoFullEditor(ID, TEMP, principal);

      assertEquals(new FullEditorAssembly(TEMP, "VSChart"), result);
      assertEquals(ORIGINAL_SIZE, temp.getPixelSize());
      verifyLockedThenFlushedAndNotCancelled();
   }

   // Bug #78734: clicked before the preview reached the client
   @Test
   void withoutNameTheLatestPreviewTakesTheOriginalSizeAndIsAnswered() throws Exception {
      addTemp(new CrosstabVSAssembly(vs, OLDER_TEMP));
      ChartVSAssembly temp = addTemp(new ChartVSAssembly(vs, TEMP));

      FullEditorAssembly result = service.gotoFullEditor(ID, null, principal);

      assertEquals(new FullEditorAssembly(TEMP, "VSChart"), result);
      assertEquals(ORIGINAL_SIZE, temp.getPixelSize());
      verifyLockedThenFlushedAndNotCancelled();
   }

   // The recommendation adds the preview under the sandbox write lock and the read lock waits
   // for it, so a preview added while waiting is the one answered.
   @Test
   void withoutNameThePreviewAddedWhileWaitingForTheLockIsUsed() throws Exception {
      ChartVSAssembly temp = new ChartVSAssembly(vs, TEMP);
      temp.setPixelSize(PREVIEW_SIZE);
      doAnswer(inv -> vs.addAssembly(temp)).when(box).lockRead();

      FullEditorAssembly result = service.gotoFullEditor(ID, null, principal);

      assertEquals(new FullEditorAssembly(TEMP, "VSChart"), result);
      assertEquals(ORIGINAL_SIZE, temp.getPixelSize());
   }

   @Test
   void withoutNameThePreviewTypeIsItsOwn() throws Exception {
      CrosstabVSAssembly temp = addTemp(new CrosstabVSAssembly(vs, TEMP));

      FullEditorAssembly result = service.gotoFullEditor(ID, null, principal);

      assertEquals(new FullEditorAssembly(TEMP, "VSCrosstab"), result);
      // a preview of another type than the original keeps its own size
      assertEquals(PREVIEW_SIZE, temp.getPixelSize());
   }

   // Bug #44232: no preview assembly (unbound chart, failed or not yet started recommendation)
   @Test
   void withoutNameAndWithoutPreviewNothingIsDone() throws Exception {
      assertNull(service.gotoFullEditor(ID, null, principal));

      verify(box).lockRead();
      verify(box).unlockRead();
      verifyNoInteractions(temporaryInfoService);
      verify(viewsheetService, never()).flushRuntimeSheet(any());
      assertEquals(ORIGINAL_SIZE, vs.getAssembly(ORIGINAL).getPixelSize());
   }

   @Test
   void withAnEmptySandboxNothingIsDone() throws Exception {
      addTemp(new ChartVSAssembly(vs, TEMP));
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());

      assertNull(service.gotoFullEditor(ID, null, principal));

      verifyNoInteractions(temporaryInfoService);
      verify(viewsheetService, never()).flushRuntimeSheet(any());
      assertEquals(PREVIEW_SIZE, vs.getAssembly(TEMP).getPixelSize());
   }

   @Test
   void anUnknownAssemblyNameIsIgnored() throws Exception {
      assertNull(service.gotoFullEditor(ID, "Missing", principal));

      verify(box).unlockRead();
      verify(viewsheetService, never()).flushRuntimeSheet(any());
   }

   @Test
   void aMissingViewsheetIsIgnored() throws Exception {
      assertNull(service.gotoFullEditor("other", null, principal));
   }

   // the result crosses the cluster through the generated proxy; the Ignite BinaryMarshaller
   // round trip is in VSWizardFullEditorAssemblyIgniteMarshalTest
   @Test
   void theResultIsSerializable() throws Exception {
      FullEditorAssembly result = new FullEditorAssembly(TEMP, "VSChart");
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(result);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         assertEquals(result, in.readObject());
      }
   }

   private <T extends VSAssembly> T addTemp(T temp) {
      temp.setPixelSize(PREVIEW_SIZE);
      vs.addAssembly(temp);
      return temp;
   }

   private void verifyLockedThenFlushedAndNotCancelled() throws Exception {
      InOrder order = inOrder(box, viewsheetService);
      order.verify(box).lockRead();
      order.verify(box).unlockRead();
      order.verify(viewsheetService).flushRuntimeSheet(ID);
      verify(box, never()).cancel(anyBoolean());
   }

   private final Principal principal = () -> "admin";
   private Viewsheet vs;
   private ViewsheetService viewsheetService;
   private RuntimeViewsheet rvs;
   private ViewsheetSandbox box;
   private VSWizardTemporaryInfoService temporaryInfoService;
   private VSWizardObjectToolbarService service;
}
