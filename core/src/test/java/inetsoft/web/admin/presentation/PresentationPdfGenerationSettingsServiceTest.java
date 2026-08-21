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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * getModel() reports the state of nine boolean pdf.* properties to the PDF Generation card.
 * PDF3Generator.getPDFGenerator() enforces the same nine with equalsIgnoreCase("true"), so a
 * value the card never writes -- INETSOFTENV_PDF_OUTPUT_ASCII, a direct property-store edit --
 * could take effect in generation while the card showed the box unticked, and the next save of
 * the card wrote the displayed false over it.
 *
 * getModel() therefore resolves each of the nine case-insensitively, matching the generator.
 *
 * Behavioral guarantees covered:
 *
 * [G1] Each boolean reads true for "true", "TRUE", and mixed case.
 * [G2] Each reads false for "false", any unrecognized value, and null.
 * [G3] openFirst resolves case-insensitively, bookmark taking precedence over thumbnail.
 * [G4] pdf.output.attachment resolves "embed" case-insensitively. It is not a boolean, but the
 *      card renders it as a checkbox and both enforcement sites (ExportControllerService and
 *      VSExportService) compare with equalsIgnoreCase, so the same split applied to it.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.presentation.model.PresentationPdfGenerationSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class PresentationPdfGenerationSettingsServiceTest {
   private PresentationPdfGenerationSettingsService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      service = new PresentationPdfGenerationSettingsService();
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private void stub(String property, String value) {
      // getModel(true) reads through getProperty(name, false, !globalProperty)
      sreeEnvStatic.when(() -> SreeEnv.getProperty(property, false, false)).thenReturn(value);
   }

   private PresentationPdfGenerationSettingsModel model() {
      return service.getModel(true);
   }

   private static Stream<Arguments> booleans() {
      return Stream.of(
         Arguments.of("pdf.compress.text",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::compressText),
         Arguments.of("pdf.compress.image",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::compressImage),
         Arguments.of("pdf.output.ascii",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::asciiOnly),
         Arguments.of("pdf.map.symbols",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::mapSymbols),
         Arguments.of("pdf.embed.cmap",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::pdfEmbedCmap),
         Arguments.of("pdf.embed.font",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::pdfEmbedFont),
         Arguments.of("pdf.generate.links",
                      (Function<PresentationPdfGenerationSettingsModel, Boolean>)
                         PresentationPdfGenerationSettingsModel::pdfHyperlinks)
      );
   }

   @ParameterizedTest(name = "{0} reads true case-insensitively")
   @MethodSource("booleans")
   void storedTrueIsDisplayedAsTicked(
      String property, Function<PresentationPdfGenerationSettingsModel, Boolean> accessor)
   {
      for(String value : new String[]{ "true", "TRUE", "True", "tRuE" }) {
         stub(property, value);
         assertTrue(accessor.apply(model()), property + "=" + value);
      }
   }

   @ParameterizedTest(name = "{0} reads false for non-true values")
   @MethodSource("booleans")
   void storedNonTrueIsDisplayedAsUnticked(
      String property, Function<PresentationPdfGenerationSettingsModel, Boolean> accessor)
   {
      for(String value : new String[]{ "false", "FALSE", "CHECKED", "yes", "1", "", null }) {
         stub(property, value);
         assertFalse(accessor.apply(model()), property + "=" + value);
      }
   }

   @Test
   void openFirstResolvesCaseInsensitively() {
      stub("pdf.open.bookmark", "TRUE");
      assertEquals("bookmark", model().openFirst());

      stub("pdf.open.bookmark", "false");
      stub("pdf.open.thumbnail", "True");
      assertEquals("thumbnail", model().openFirst());

      // bookmark wins when both are set
      stub("pdf.open.bookmark", "true");
      assertEquals("bookmark", model().openFirst());
   }

   @Test
   void openFirstIsNullWhenNeitherIsSet() {
      assertNull(model().openFirst());

      stub("pdf.open.bookmark", "CHECKED");
      stub("pdf.open.thumbnail", "no");
      assertNull(model().openFirst());
   }

   @Test
   void browserEmbedPdfResolvesCaseInsensitively() {
      for(String value : new String[]{ "embed", "EMBED", "Embed" }) {
         stub("pdf.output.attachment", value);
         assertTrue(model().browserEmbedPdf(), "pdf.output.attachment=" + value);
      }

      // "true" is what the card writes for the unticked state
      for(String value : new String[]{ "true", "attachment", "", null }) {
         stub("pdf.output.attachment", value);
         assertFalse(model().browserEmbedPdf(), "pdf.output.attachment=" + value);
      }
   }
}
