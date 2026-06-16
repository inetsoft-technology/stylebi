/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

@Tag("core")
class WorksheetEventServiceSocketSessionTest {
   @Test
   void recordSocketSessionSetsItOnRuntime() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      WorksheetEventService.recordSocketSession(rws, "stomp-session-42");
      verify(rws).setSocketSessionId("stomp-session-42");
   }

   @Test
   void recordSocketSessionTolerantOfNulls() {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      WorksheetEventService.recordSocketSession(rws, null);   // must not throw, must not call setter
      WorksheetEventService.recordSocketSession(null, "x");   // must not throw
      verify(rws, never()).setSocketSessionId(any());
   }
}
