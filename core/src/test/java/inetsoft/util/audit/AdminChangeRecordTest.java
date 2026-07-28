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
package inetsoft.util.audit;

import org.junit.jupiter.api.*;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class AdminChangeRecordTest {
   @Test
   void validWhenRequiredFieldsPresent() {
      AdminChangeRecord r = new AdminChangeRecord();
      r.setTransactionId("chg-1");
      r.setProperty("max.rows");
      r.setAction(AdminChangeRecord.ACTION_APPLY);
      r.setActionTimestamp(new Timestamp(0L));
      assertTrue(r.isValid());
   }

   @Test
   void invalidWhenTransactionIdMissing() {
      AdminChangeRecord r = new AdminChangeRecord();
      r.setProperty("max.rows");
      r.setAction(AdminChangeRecord.ACTION_APPLY);
      assertFalse(r.isValid());
   }

   @Test
   void roundTripsBeforeAndAfterValues() {
      AdminChangeRecord r = new AdminChangeRecord();
      r.setBeforeValue("100");
      r.setAfterValue("500");
      assertEquals("100", r.getBeforeValue());
      assertEquals("500", r.getAfterValue());
   }
}
