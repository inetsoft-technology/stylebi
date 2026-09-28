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
package inetsoft.web.portal.controller.database;

/*
 * Bug #77063: /api/portal/data/autodrill/worksheet/fields loads the client-identified worksheet
 * with the READ check (which rejects another org), like its /worksheet/params sibling, and lets
 * the permission error propagate.
 */

import inetsoft.uql.asset.*;
import inetsoft.util.MessageException;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataAutoDrillControllerTest {
   private static final String WS_ID = "1^2^__NULL__^Target^orgb_id";

   @Test
   void getWorksheetFields_checksReadPermission() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal principal = mock(Principal.class);
      when(repository.getSheet(any(AssetEntry.class), any(), anyBoolean(), any()))
         .thenReturn(new Worksheet());

      // an empty worksheet has no primary table
      assertNull(controller(repository).getWorksheetFields(WS_ID, principal));
      verify(repository).getSheet(argThat(e -> WS_ID.equals(e.toIdentifier())), same(principal),
                                  eq(true), eq(AssetContent.ALL));
   }

   @Test
   void getWorksheetFields_unreadableWorksheet_propagatesError() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.getSheet(any(AssetEntry.class), any(), eq(true), any()))
         .thenThrow(new MessageException("Read access denied"));

      assertThrows(MessageException.class,
         () -> controller(repository).getWorksheetFields(WS_ID, mock(Principal.class)));
   }

   private static DataAutoDrillController controller(AssetRepository repository) {
      DataAutoDrillController controller = new DataAutoDrillController();
      ReflectionTestUtils.setField(controller, "engine", repository);
      return controller;
   }
}
