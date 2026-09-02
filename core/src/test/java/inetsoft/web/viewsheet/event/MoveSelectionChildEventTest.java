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
package inetsoft.web.viewsheet.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the payload sent by the selection container child drag-and-drop can be
 * deserialized. Without @JsonDeserialize on the immutable interface, Jackson fails with
 * "Cannot construct instance of MoveSelectionChildEvent".
 */
@Tag("core")
class MoveSelectionChildEventTest {
   @Test
   void deserializeMoveChildPayload() throws Exception {
      final String json = "{\"fromIndex\":1,\"toIndex\":0,\"currentSelection\":false}";
      MoveSelectionChildEvent event =
         new ObjectMapper().readValue(json, MoveSelectionChildEvent.class);

      assertEquals(1, event.getFromIndex());
      assertEquals(0, event.getToIndex());
      assertFalse(event.isCurrentSelection());
   }

   @Test
   void deserializeCurrentSelectionPayload() throws Exception {
      final String json = "{\"fromIndex\":0,\"toIndex\":2,\"currentSelection\":true}";
      MoveSelectionChildEvent event =
         new ObjectMapper().readValue(json, MoveSelectionChildEvent.class);

      assertEquals(0, event.getFromIndex());
      assertEquals(2, event.getToIndex());
      assertTrue(event.isCurrentSelection());
   }
}
