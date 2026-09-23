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
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IgniteCluster.rethrowAffinityCause scenario table:
 *  [ExpiredSheet]       remote sheet expired       -> rethrown as ExpiredSheetException
 *  [MessageException]   remote user-facing message -> rethrown as MessageException
 *  [ConfirmException]   remote confirmation prompt -> rethrown as ConfirmException
 *  [RuntimeException]   remote unchecked failure   -> rethrown as-is
 *  [SecurityException]  remote permission failure  -> wrapped, but the cause is the
 *                                                    SecurityException itself
 *  [Checked exception]  remote checked failure     -> wrapped, cause preserved
 *  [No cause]           ExecutionException w/ null cause -> wrapped, no NPE
 *
 * A remote affinity failure has to reach the caller looking like the same failure raised
 * locally. Locally, affinityCall wraps a checked cause once: RuntimeException(cause). Remotely,
 * every cause used to be wrapped around the ExecutionException instead, so the permission failure
 * behind the "<user> did not log in" reports arrived as
 * RuntimeException(ExecutionException(SecurityException)) -- one level deeper than the identical
 * local failure, and unchecked causes lost their type entirely.
 *
 * Note inetsoft.sree.security.SecurityException is a checked exception, so it cannot be rethrown
 * as-is from affinityCall; the guarantee for it is that it sits directly under the wrapper.
 */
@Tag("core")
class IgniteClusterAffinityErrorTest {
   // [Scenario: SecurityException] the case behind the "did not log in" reports. It is a checked
   // exception, so it cannot be rethrown as-is, but it must sit directly under the wrapper -- the
   // same shape the local branch of affinityCall produces -- rather than under an extra
   // ExecutionException that hides it from callers inspecting getCause().
   @Test
   void securityExceptionCause_wrappedDirectlyNotUnderExecutionException() {
      SecurityException cause = new SecurityException("admin(agile) did not log in.");

      RuntimeException thrown = assertThrows(
         RuntimeException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause)));

      assertSame(cause, thrown.getCause(),
                 "the remote failure must be the direct cause, matching the local branch");
   }

   // [Scenario: RuntimeException] an unchecked remote failure keeps its own type
   @Test
   void runtimeExceptionCause_rethrownWithOriginalType() {
      IllegalStateException cause = new IllegalStateException("bad state");

      assertSame(cause, assertThrows(
         IllegalStateException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause))));
   }

   @Test
   void expiredSheetExceptionCause_rethrownWithOriginalType() {
      ExpiredSheetException cause = new ExpiredSheetException("sheet-1", null);

      assertSame(cause, assertThrows(
         ExpiredSheetException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause))));
   }

   @Test
   void messageExceptionCause_rethrownWithOriginalType() {
      MessageException cause = new MessageException("boom");

      assertSame(cause, assertThrows(
         MessageException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause))));
   }

   @Test
   void confirmExceptionCause_rethrownWithOriginalType() {
      ConfirmException cause = new ConfirmException("confirm");

      assertSame(cause, assertThrows(
         ConfirmException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause))));
   }

   // [Scenario: checked exception] a checked cause cannot be rethrown as-is, but it must not be
   // buried an extra level deep either -- the wrapper's cause is the remote failure itself
   @Test
   void checkedExceptionCause_wrappedWithCausePreserved() {
      IOException cause = new IOException("io");

      RuntimeException thrown = assertThrows(
         RuntimeException.class,
         () -> IgniteCluster.rethrowAffinityCause(new ExecutionException(cause)));

      assertSame(cause, thrown.getCause());
   }

   // [Scenario: no cause] a pattern switch over a null cause would NPE
   @Test
   void nullCause_wrappedWithoutNullPointerException() {
      ExecutionException ex = new ExecutionException("no cause", null);

      RuntimeException thrown = assertThrows(
         RuntimeException.class, () -> IgniteCluster.rethrowAffinityCause(ex));

      assertSame(ex, thrown.getCause());
   }
}
