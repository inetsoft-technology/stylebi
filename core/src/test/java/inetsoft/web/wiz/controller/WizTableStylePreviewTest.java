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
package inetsoft.web.wiz.controller;

import inetsoft.report.LibManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.web.composer.tablestyle.TableStyleFormatModel;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.wiz.service.WizTableStylePreviewRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Renders the preview for real (no mocked renderer) so the sample table, page size and pixel
 * output are exercised, not just the controller's argument handling.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizTableStylePreviewTest {
   @BeforeAll
   static void headless() {
      System.setProperty("java.awt.headless", "true");
   }

   @Test
   void draftPreview_returnsPngAndHeaderBackgroundIsPainted() throws Exception {
      Fixture f = new Fixture();
      BufferedImage red = decode(f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, f.draft("#ff0000")),
         f.principal));
      BufferedImage blue = decode(f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, f.draft("#0000ff")),
         f.principal));

      assertTrue(red.getWidth() > 100 && red.getHeight() > 50, "non-trivial image");
      int redInHeader = countInHeaderBand(red, 0xffff0000);
      int blueInHeader = countInHeaderBand(red, 0xff0000ff);

      assertTrue(redInHeader > 100, "header background painted, pixels=" + redInHeader);
      assertEquals(0, blueInHeader);
      assertTrue(countInHeaderBand(blue, 0xff0000ff) > 100);
      assertEquals(0, countInHeaderBand(blue, 0xffff0000));
   }

   @Test
   void draftPreview_showsWholeSampleTable() throws Exception {
      Fixture f = new Fixture();
      BufferedImage img = decode(f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, f.draft("#ff0000")),
         f.principal));

      // Header row + 4 data rows + grand total must all fit: not the 1-2 row crop a too-small
      // page would give.
      assertTrue(img.getHeight() >= 6 * 12, "all sample rows rendered, height=" + img.getHeight());
      assertTrue(img.getWidth() >= 6 * 20, "all sample columns rendered, width=" + img.getWidth());
   }

   @Test
   void savedPreview_matchesDraftPreviewOfTheSameFormat() throws Exception {
      Fixture f = new Fixture();
      TableStyleFormatModel format = f.draft("#00ff00");
      XTableStyle saved = new XTableStyle(f.tableStyleService.getTableModel());
      f.tableStyleService.initTableStyle(saved);
      format.updateTableStyle(saved);
      saved.setID("1001");
      saved.setName("User Defined~Green");
      when(f.lib.getTableStyle("1001")).thenReturn(saved);

      WizTableStyleController.TablePreview byId = f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest("1001", null), f.principal);
      WizTableStyleController.TablePreview byDraft = f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, format), f.principal);

      assertEquals(byDraft.image(), byId.image());
      verify(f.lib, never()).setTableStyle(anyString(), any());
      verify(f.lib, never()).save();
   }

   @Test
   void preview_requiresExactlyOneOfStyleIdOrFormat() {
      Fixture f = new Fixture();

      assertThrows(IllegalArgumentException.class, () -> f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, null), f.principal));
      assertThrows(IllegalArgumentException.class, () -> f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest("1001", f.draft("#ff0000")),
         f.principal));
   }

   @Test
   void preview_unknownStyleIdIsRefused() {
      Fixture f = new Fixture();
      when(f.lib.getTableStyle("nope")).thenReturn(null);

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest("nope", null), f.principal));
      assertTrue(e.getMessage().contains("nope"));
   }

   @Test
   void preview_incompleteFormatIsRefusedByName() {
      Fixture f = new Fixture();

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest(null, new TableStyleFormatModel()),
         f.principal));
      assertTrue(e.getMessage().contains("headerRowFormat"));
   }

   @Test
   void preview_byIdWithoutReadPermissionIsDenied() throws Exception {
      Fixture f = new Fixture();
      XTableStyle saved = new XTableStyle(f.tableStyleService.getTableModel());
      saved.setID("1001");
      saved.setName("Secret");
      when(f.lib.getTableStyle("1001")).thenReturn(saved);
      when(f.securityEngine.checkPermission(any(), eq(ResourceType.TABLE_STYLE), eq("Secret"),
                                            eq(ResourceAction.READ))).thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.preview(
         new WizTableStyleController.PreviewTableStyleRequest("1001", null), f.principal));
   }

   private static int countInHeaderBand(BufferedImage img, int argb) {
      int count = 0;

      for(int y = 0; y < Math.min(10, img.getHeight()); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            if(img.getRGB(x, y) == argb) {
               count++;
            }
         }
      }

      return count;
   }

   private static BufferedImage decode(WizTableStyleController.TablePreview preview)
      throws Exception
   {
      byte[] png = Base64.getDecoder().decode(preview.image());
      assertEquals((byte) 0x89, png[0]);
      assertEquals((byte) 'P', png[1]);
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
      assertEquals(img.getWidth(), preview.width());
      assertEquals(img.getHeight(), preview.height());
      return img;
   }

   private static final class Fixture {
      Fixture() {
         try {
            when(securityEngine.checkPermission(any(), any(ResourceType.class),
                                                nullable(String.class),
                                                any(ResourceAction.class))).thenReturn(true);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         when(libManagerProvider.getManager(any(Principal.class))).thenReturn(lib);
         controller = new WizTableStyleController(
            libManagerProvider, tableStyleService, securityEngine,
            new WizTableStylePreviewRenderer(libManagerProvider, mock(Cluster.class)));
      }

      TableStyleFormatModel draft(String headerBackground) {
         XTableStyle style = new XTableStyle(tableStyleService.getTableModel());
         tableStyleService.initTableStyle(style);
         TableStyleFormatModel format = new TableStyleFormatModel(style);
         format.getHeaderRowFormat().setBackground(headerBackground);
         return format;
      }

      final LibManagerProvider libManagerProvider = mock(LibManagerProvider.class);
      final TableStyleService tableStyleService =
         new TableStyleService(null, null, libManagerProvider);
      final SecurityEngine securityEngine = mock(SecurityEngine.class);
      final LibManager lib = mock(LibManager.class);
      final Principal principal = mock(Principal.class);
      final WizTableStyleController controller;
   }
}
