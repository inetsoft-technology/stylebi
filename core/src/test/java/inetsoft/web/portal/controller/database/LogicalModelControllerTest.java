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
 * Bug #77063: /api/data/logicalModel/vs/autoDrill-parameters loads the client-identified
 * viewsheet with the READ check (which rejects another org), and a viewsheet the caller cannot
 * read yields no parameters, the same as a missing one.
 */

import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
class LogicalModelControllerTest {
   private static final String VS_ID = "1^128^__NULL__^Target^orgb_id";

   @Test
   void getViewsheetParameters_checksReadPermission() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal principal = mock(Principal.class);
      when(repository.getSheet(any(AssetEntry.class), any(), anyBoolean(), any()))
         .thenReturn(new Viewsheet());

      assertArrayEquals(new String[0], controller(repository)
         .getViewsheetParameters(VS_ID, principal));
      verify(repository).getSheet(argThat(e -> VS_ID.equals(e.toIdentifier())), same(principal),
                                  eq(true), eq(AssetContent.NO_DATA));
   }

   @Test
   void getViewsheetParameters_unreadableViewsheet_returnsEmpty() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.getSheet(any(AssetEntry.class), any(), eq(true), any()))
         .thenThrow(new MessageException("Read access denied"));

      assertArrayEquals(new String[0], controller(repository)
         .getViewsheetParameters(VS_ID, mock(Principal.class)));
   }

   private static LogicalModelController controller(AssetRepository repository) {
      return new LogicalModelController(repository, null, null, null, null, null);
   }
}
