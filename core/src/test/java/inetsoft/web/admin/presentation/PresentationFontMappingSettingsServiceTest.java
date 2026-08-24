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
 * getModel() parses the "pdf.font.mapping" property, a ';'-separated list of
 * "trueTypeFont:cidFont" pairs. The property can hold values that the Font Mapping
 * card never writes (Settings > All Properties, INETSOFTENV_PDF_FONT_MAPPING, a direct
 * property-store edit), so a malformed entry must not propagate out of the model build --
 * that failed the whole Presentation settings page, not just the Font Mapping card.
 *
 * PDF3Generator.getPDFGenerator() reads the same property and silently skips any entry
 * that does not yield a font pair, so getModel() skips them too.
 *
 * Behavioral guarantees covered:
 *
 * [G1] A well-formed list parses into one model per entry, split at the first colon.
 * [G2] An entry with no colon is skipped instead of throwing.
 * [G3] Null or empty property yields an empty list.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.presentation.model.PresentationFontMappingModel;
import inetsoft.web.admin.presentation.model.PresentationFontMappingSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class PresentationFontMappingSettingsServiceTest {
   private PresentationFontMappingSettingsService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      service = new PresentationFontMappingSettingsService();
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private List<PresentationFontMappingModel> parse(String property) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("pdf.font.mapping")).thenReturn(property);
      PresentationFontMappingSettingsModel model = service.getModel();
      return model.fontMappings();
   }

   @Test
   void wellFormedEntriesAreParsed() {
      List<PresentationFontMappingModel> mappings = parse("Arial:STSong-Light;Courier:MSung-Light");

      assertEquals(2, mappings.size());
      assertEquals("Arial", mappings.get(0).trueTypeFont());
      assertEquals("STSong-Light", mappings.get(0).cidFont());
      assertEquals("Courier", mappings.get(1).trueTypeFont());
      assertEquals("MSung-Light", mappings.get(1).cidFont());
   }

   @Test
   void entryWithoutColonIsSkipped() {
      List<PresentationFontMappingModel> mappings =
         assertDoesNotThrow(() -> parse("Arial:STSong-Light;NoColonHere;Courier:MSung-Light"));

      assertEquals(2, mappings.size());
      assertEquals("Arial", mappings.get(0).trueTypeFont());
      assertEquals("Courier", mappings.get(1).trueTypeFont());
   }

   @Test
   void onlyMalformedEntryYieldsEmptyList() {
      assertTrue(assertDoesNotThrow(() -> parse("NoColonHere")).isEmpty());
   }

   @Test
   void nullOrEmptyPropertyYieldsEmptyList() {
      assertTrue(parse(null).isEmpty());
      assertTrue(parse("").isEmpty());
   }
}
