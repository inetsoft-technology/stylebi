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
package inetsoft.test.lockorder.boot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * The dispatch point the lock-order recorder's advice calls from inside instrumented lock
 * classes. {@link inetsoft.test.lockorder.LockOrderRecorder} injects this class into the
 * bootstrap class loader, so JDK classes ({@code ReentrantLock}) and application classes
 * ({@code LendableReentrantLock}) reach the same copy. It uses only bootstrap types, and does
 * nothing while no sink is set. Test code only; never referenced by product code.
 */
public final class LockHook {
   private LockHook() {
   }

   public static void event(Object lock, int kind) {
      BiConsumer<Object, Integer> target = sink;

      if(target != null) {
         try {
            target.accept(lock, kind);
         }
         catch(Throwable ex) {
            // the recorder must never change the behavior of the lock it observes, but a
            // swallowed error is a gap in the graph: count it
            errors.incrementAndGet();
         }
      }
   }

   public static final int CREATED = 0;
   public static final int ENTER = 1;
   public static final int ACQUIRED = 2;
   public static final int FAILED = 3;
   public static final int TRY_ACQUIRED = 4;
   public static final int TIMED_ACQUIRED = 5;
   public static final int UNLOCK = 6;
   public static final int TIMED_ENTER = 7;

   public static volatile BiConsumer<Object, Integer> sink;
   public static final AtomicLong errors = new AtomicLong();
}
