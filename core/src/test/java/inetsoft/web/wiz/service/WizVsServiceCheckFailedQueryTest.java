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
package inetsoft.web.wiz.service;

import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.lens.DefaultTableLens;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The failed-query error must surface the underlying data-source cause (the real JDBC/SQL error)
 * when the failed-query table carries one, instead of only the generic "query failed" text.
 * Regression for the swallowed-error bug (Redmine #75484).
 */
@Tag("core")
class WizVsServiceCheckFailedQueryTest {
   @Test
   void surfacesUnderlyingCauseFromProperty() {
      // The chart path replaces the failed meta table with generated sample data and carries the
      // real cause as the FAILED_QUERY_PROPERTY value (see ChartVSAQuery).
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      lens.setProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY,
                       "ERROR: column company_company.supplier_id does not exist");

      String cause = WizVsService.failedQueryCause(lens);
      assertTrue("ERROR: column company_company.supplier_id does not exist".equals(cause),
                 "should extract the cause from the property, was: " + cause);

      String message = WizVsService.failedQueryError(cause);
      // The original generic guidance is preserved...
      assertTrue(message.contains("Worksheet query failed"),
                 "should keep the generic guidance, was: " + message);
      // ...and the real cause is appended so the caller can act on it.
      assertTrue(message.contains("company_company.supplier_id does not exist"),
                 "should surface the underlying cause, was: " + message);
   }

   @Test
   void stillReportsFailureWithGenericWhenNoCause() {
      // Legacy bare "true" flag (no cause captured): still detected as failed, but no "(cause:".
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      lens.setProperty(XNodeMetaTable.FAILED_QUERY_PROPERTY, "true");

      String cause = WizVsService.failedQueryCause(lens);
      assertTrue("".equals(cause), "failed-with-no-cause should be empty string, was: " + cause);

      String message = WizVsService.failedQueryError(cause);
      assertTrue(message.contains("Worksheet query failed"),
                 "should keep the generic guidance, was: " + message);
      assertFalse(message.contains("(cause:"),
                  "should not append an empty cause, was: " + message);
   }

   @Test
   void returnsNullForCleanTable() {
      DefaultTableLens lens = new DefaultTableLens(1, 1);
      assertNull(WizVsService.failedQueryCause(lens),
                 "a table with no failed-query marker is not a failure");
   }
}
