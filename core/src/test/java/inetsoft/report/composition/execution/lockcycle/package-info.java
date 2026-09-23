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

/**
 * Lock-cycle regression suite for the GraalJS engine-lock / lens-monitor deadlock family
 * (bug #76966). Every case builds a pipeline from real classes with
 * {@link inetsoft.report.composition.execution.lockcycle.LockCycleHarness} and runs it on
 * daemon threads with bounded waits, so a cycle fails fast with a thread dump.
 *
 * <p>Cases of cycles fixed on main run by default. Cases of cycles still open are tagged
 * {@code known-deadlock} and run only with {@code -Dlockcycle.known=true}; on current main
 * each of them fails by timeout. A fix or redesign turns them on and must make them pass:
 * <pre>
 * ./mvnw -o -pl core test -Dtest='inetsoft.report.composition.execution.lockcycle.*Test' -Dlockcycle.known=true
 * </pre>
 *
 * <p>Threads are ordered by observed state (gates in the base tables, {@code awaitParked}, {@code awaitWaitingOnLock},
 * latches), not by sleeping, so a slow machine cannot flip a case into, or out of, its cycle.
 * Known cases wait at most {@code KNOWN_CAP} (10 s) per step; on a very slow machine a fixed
 * pipeline could exceed that, so read a known case's failure dump before concluding a cycle is
 * still open.
 *
 * <p>Classes of this suite:
 * <ul>
 * <li>{@code JoinWorkerCycleTest}: #76960 A, CrossJoin/HashJoin/MergeJoin workers.</li>
 * <li>{@code MonitorFirstLensCycleTest}: #76960 B (R2), Sort/MaxRows/Union/Ranking monitors.</li>
 * <li>{@code CrossSandboxCycleTest}: #76960 B (R2-X, R2-X′), #76964 (R3), #76938.</li>
 * <li>{@code BoxResetCycleTest}: #76961, reset/dispose of the building sandbox.</li>
 * <li>{@code ScriptThreadGuardCycleTest}: #76960 C, isScriptThread-only sandbox lock guard.</li>
 * <li>{@code SubQueryConditionCycleTest}: #76965, sub-query condition tables under the filter's monitor.</li>
 * <li>{@code GuestReaderCycleTest}: true guest (in-{@code exec}) holders: #76918 shapes, unions,
 * guest variants of #76960 A/B and #76964, and a reader racing {@code invalidate()}.</li>
 * </ul>
 *
 * <p>Earlier tests of the same family stay where they are and belong to the suite:
 * <ul>
 * <li>{@code inetsoft.report.composition.execution.ConditionFilterGraalLockOrderingTest}: #76918.</li>
 * <li>{@code inetsoft.report.composition.execution.AsyncLensScriptLockLendingTest}: #76938,
 * #76943, #76937 (build-time, first-touch, stacked, exec-worker and shared-lens orderings).</li>
 * <li>{@code inetsoft.report.composition.execution.ConditionFilterFormulaScopingTest}: #76935.</li>
 * <li>{@code inetsoft.report.composition.execution.ViewsheetSandboxScriptLockOrderingTest},
 * {@code inetsoft.util.script.graal.GraalJavaScriptEnvLockOrderingTest},
 * {@code inetsoft.report.lens.CalcTableLensScopeInitLockOrderingTest}: #76905 (#5537).</li>
 * <li>{@code inetsoft.util.script.ScriptThreadLocalsInitRaceTest},
 * {@code inetsoft.util.script.LendableReentrantLockTest}.</li>
 * </ul>
 */
package inetsoft.report.composition.execution.lockcycle;
