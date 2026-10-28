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

package inetsoft.util.dep;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79236, the import of a data cycle refuses a time condition whose hour, minute or second is
 * out of range before it is stored, and imports the same condition in range.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataCycleAssetRangeTest {
   @Test
   void hourOutOfRange_isRefusedAndNothingIsStored() throws Exception {
      DataCycleManager manager = mock(DataCycleManager.class);

      try(MockedStatic<DataCycleManager> managerStatic = mockStatic(DataCycleManager.class)) {
         managerStatic.when(DataCycleManager::getDataCycleManager).thenReturn(manager);

         assertThrows(IllegalArgumentException.class, () -> parse(-1, 30));
         assertThrows(IllegalArgumentException.class, () -> parse(0, -30));
         assertThrows(IllegalArgumentException.class, () -> parse(2, 60));
         verify(manager, never()).setConditions(anyString(), any(), any());

         parse(0, 30);
         verify(manager).setConditions(anyString(), any(), any());
      }
   }

   private static void parse(int hour, int minute) throws Exception {
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><DataCycle name=\"cycle\">" +
         "<Condition type=\"TimeCondition\" timeType=\"8\" hour=\"" + hour + "\" minute=\"" +
         minute + "\" second=\"0\" hour_end=\"-1\" minute_end=\"-1\" second_end=\"-1\" " +
         "hourlyInterval=\"0.5\" daysOfWeek=\"1,2,3,4,5,6,7\"/></DataCycle>";
      DataCycleAsset asset = new DataCycleAsset();
      asset.parseIdentifier("cycle", null);
      asset.parseContent(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), null,
                         true, true);
   }
}
