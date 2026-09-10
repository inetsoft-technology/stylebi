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
package inetsoft.uql.viewsheet.internal;

import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #76522 (DCG-003): {@code isDateComparisonDefined(info, checkShareFrom)} returns the
 * {@code checkShareFrom} argument verbatim, with no further lookup, whenever the assembly's own
 * {@code comparisonShareFrom} is set — it never asks whether the share source's comparison is
 * actually live. Every render-facing caller uses the no-arg overload (hardcoded
 * {@code checkShareFrom=true}); {@code DateComparisonDialogService.isDateComparisonEnabled()} was
 * the sole caller passing {@code false} (copy-pasted from {@code getShare()}'s own, different-
 * purpose usage), which made {@code get_date_comparison} always report a sharing assembly as
 * disabled, regardless of whether the share is genuinely live. This test exercises the real,
 * unmocked predicate directly, so a future caller cannot silently reintroduce the wrong argument
 * value without this test documenting the asymmetry is intentional.
 */
@WizAgentTestSupport
class DateComparisonUtilTest {
   @Test
   void checkShareFromArgumentAloneDecidesASharingAssemblysDefinedness() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setComparisonShareFrom("Chart1");

      assertTrue(DateComparisonUtil.isDateComparisonDefined(info, true),
                 "checkShareFrom=true (every rendering-facing caller's convention) must treat " +
                 "a configured shareAssembly as a defined comparison");
      assertFalse(DateComparisonUtil.isDateComparisonDefined(info, false),
                  "checkShareFrom=false must still report a sharing assembly as not-defined -- " +
                  "this is getShare()'s own, deliberately different, 'exclude an already-" +
                  "sharing assembly from the share-from candidate list' semantics, and is not " +
                  "the argument value get_date_comparison's enabled check should use");
   }

   /** The no-arg overload is what every rendering-facing caller actually uses. */
   @Test
   void noArgOverloadDefaultsToCheckingShareFrom() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setComparisonShareFrom("Chart1");

      assertTrue(DateComparisonUtil.isDateComparisonDefined(info));
   }
}
