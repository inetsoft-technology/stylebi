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
package inetsoft.web.wiz.viewsheet;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.SingleTimeInfo;
import inetsoft.uql.viewsheet.TimeInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * bug-79195: a JSON null in {@code values}/{@code deselect} must survive the request DTO, and a
 * range slider asked for the blank must be refused by name, not fail with a null dereference.
 */
@Tag("core")
class SelectionRuntimeServiceBlankItemTransportTest {
   @Test
   void aJsonNullPathSegmentSurvivesDeserialization() throws Exception {
      String json = "{\"assembly\":\"L\",\"values\":[[null]],\"deselect\":[[null],[\"x\"]]}";
      ViewsheetAssemblyAgentController.SelectionRequest request = new ObjectMapper().readValue(
         json, ViewsheetAssemblyAgentController.SelectionRequest.class);

      assertEquals(1, request.values().size());
      assertEquals(1, request.values().get(0).size());
      assertNull(request.values().get(0).get(0));
      assertNull(request.deselect().get(0).get(0));
      assertEquals("x", request.deselect().get(1).get(0));
   }

   private static List<List<String>> blankPath() {
      List<List<String>> values = new ArrayList<>();
      values.add(Arrays.asList((String) null));
      return values;
   }

   @Test
   void aNumberSliderRefusesTheBlankByName() {
      SelectionValue[] buckets = { new SelectionValue("1", "1"), new SelectionValue("2", "2") };

      Exception e = assertThrows(IllegalArgumentException.class, () ->
         SelectionRuntimeService.sliderBucketRange("S", buckets, null, blankPath(),
                                                   new ArrayList<>()));

      assertTrue(e.getMessage().contains("no bucket matching 'null'"), e.getMessage());
   }

   @Test
   void aDateSliderRefusesTheBlankByName() {
      SingleTimeInfo info = mock(SingleTimeInfo.class);
      when(info.getRangeType()).thenReturn(TimeInfo.MONTH);
      when(info.isDateTime()).thenCallRealMethod();
      SelectionValue[] buckets = { new SelectionValue("2023-01", "{m '2023-01'}"),
                                   new SelectionValue("2023-02", "{m '2023-02'}") };

      Exception e = assertThrows(IllegalArgumentException.class, () ->
         SelectionRuntimeService.sliderBucketRange("S", buckets, info, blankPath(),
                                                   new ArrayList<>()));

      assertTrue(e.getMessage().contains("no bucket matching 'null'"), e.getMessage());
   }
}
