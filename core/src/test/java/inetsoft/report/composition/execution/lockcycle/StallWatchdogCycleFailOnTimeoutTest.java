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
package inetsoft.report.composition.execution.lockcycle;

import org.junit.jupiter.api.Tag;

/**
 * The cycles of {@link StallWatchdogCycleTest} with {@code stall.watchdog.failOnTimeout=true}:
 * a fail-mode wait fails on the timeout alone, without a confirmed cycle, as fail did before
 * Feature #77123. The alert-mode and slow-pipeline cases do not depend on the rule and run
 * in the parent class only.
 */
@Tag("slow")
public class StallWatchdogCycleFailOnTimeoutTest extends StallWatchdogCycleTest {
   @Override
   protected boolean failOnTimeout() {
      return true;
   }

   // not a @Test here: alert mode has no fail rule
   @Override
   public void monitorFirstLensAlertModeTurnsHealthDown() {
   }

   // not a @Test here: it sets its own policy
   @Override
   public void slowProgressingSummaryUnderLockCompletes() {
   }
}
