/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BinaryOperator;

/*
  Internal note: see the unit test cases in DefaultDebouncerTest for examples of the use of each
  debounce method.
 */

/**
 * {@code DefaultDebouncer} is an implementation of {@link Debouncer} that uses a local, in-memory
 * queue of debounced tasks.
 *
 * @param <T> the type of key used to identify duplicate tasks.
 */
public class DefaultDebouncer<T> implements Debouncer<T> {
   /**
    * Creates a new instance of {@code DefaultDebouncer} that preserves the sequence of tasks.
    */
   public DefaultDebouncer() {
      this(true);
   }

   /**
    * Creates a new instance of {@code DefaultDebouncer}.
    *
    * @param preserveSequence {@code true} to preserve the sequence of tasks with different keys.
    *                         That is, if a task with key <i>a</i> is submitted, then another task
    *                         with key <i>a</i> is submitted, then the tasks will be debounced.
    *                         However, if a task with key <i>a</i> is submitted, then a task with
    *                         key <i>b</i>, and then a task with key <i>a</i>, the third task will
    *                         <b>not</b> be debounced with the first task.
    */
   public DefaultDebouncer(boolean preserveSequence) {
      this(preserveSequence, GroupedThread::new);
   }

   /**
    * Creates a new instance of {@code DefaultDebouncer}.
    *
    * @param preserveSequence {@code true} to preserve the sequence of tasks with different keys.
    *                         That is, if a task with key <i>a</i> is submitted, then another task
    *                         with key <i>a</i> is submitted, then the tasks will be debounced.
    *                         However, if a task with key <i>a</i> is submitted, then a task with
    *                         key <i>b</i>, and then a task with key <i>a</i>, the third task will
    *                         <b>not</b> be debounced with the first task.
    * @param threadFactory    the factory that handles creating threads for the debouncer.
    */
   public DefaultDebouncer(boolean preserveSequence, ThreadFactory threadFactory) {
      this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
      this.preserveSequence = preserveSequence;
   }

   /**
    * Debounces a task so that only the most recent task with the same key submitted in the current
    * interval is executed.
    *
    * @param key          the key that identifies the task.
    * @param interval     the amount of time to wait before performing the task.
    * @param intervalUnit the unit of time for the interval.
    * @param task         the task to run.
    */
   public void debounce(T key, long interval, TimeUnit intervalUnit, Runnable task) {
      schedule(key, interval, intervalUnit, new RunnableCallable(task), this::replace);
   }

   /**
    * Debounces a task so that only one runs in the current interval for the specified key.
    * <p>
    * The reducer handles combining
    * tasks within an interval. The first argument to the reducer function is the current, pending
    * task (may be {@code null}. The second argument is the task passed in this invocation. The
    * reducer function should return a task that combines the two tasks. The returned task will then
    * replace the current, pending task.
    *
    * @param key          the key that identifies the task.
    * @param interval     the amount of time to wait before performing the task.
    * @param intervalUnit the unit of time for the interval.
    * @param task         the task to run.
    * @param reducer      the reducer function that combines pending tasks.
    */
   public void debounce(T key, long interval, TimeUnit intervalUnit, Runnable task,
                        BinaryOperator<Runnable> reducer)
   {
      schedule(key, interval, intervalUnit, new RunnableCallable(task), new RunnableReducer(reducer));
   }

   /**
    * Debounces a task so that only the most recent task with the same key submitted in the current
    * interval is executed.
    *
    * @param key          the key that identifies the task.
    * @param interval     the amount of time to wait before performing the task.
    * @param intervalUnit the unit of time for the interval.
    * @param task         the task to run.
    *
    * @param <V> the return type of the task.
    *
    * @return a {@link Future} that will return the result of the task that is finally executed.
    */
   public <V> Future<V> debounce(T key, long interval, TimeUnit intervalUnit, Callable<V> task) {
      return schedule(key, interval, intervalUnit, task, this::replace);
   }

   /**
    * Debounces a task so that only one runs in the current interval for the specified key.
    * <p>
    * The reducer handles combining
    * tasks within an interval. The first argument to the reducer function is the current, pending
    * task (may be {@code null}. The second argument is the task passed in this invocation. The
    * reducer function should return a task that combines the two tasks. The returned task will then
    * replace the current, pending task.
    *
    * @param key          the key that identifies the task.
    * @param interval     the amount of time to wait before performing the task.
    * @param intervalUnit the unit of time for the interval.
    * @param task         the task to run.
    * @param reducer      the reducer function that combines pending tasks.
    *
    * @param <V> the return type of the task.
    *
    * @return a {@link Future} that will return the result of the task that is finally executed.
    */
   public <V> Future<V> debounce(T key, long interval, TimeUnit intervalUnit, Callable<V> task,
                                 BinaryOperator<Callable<V>> reducer)
   {
      return schedule(key, interval, intervalUnit, task, reducer);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws Exception {
      executor.shutdown();
   }

   @SuppressWarnings("unchecked")
   private <V> Future<V> schedule(T key, long interval, TimeUnit intervalUnit, Callable<V> task,
                                  BinaryOperator<Callable<V>> reducer)
   {
      lock.lock();

      try {
         DebouncedTask<V> debounced;

         if(preserveSequence) {
            if(Objects.equals(lastKey, key)) {
               debounced = (DebouncedTask<V>) lastTask;
            }
            else {
               debounced = new DebouncedTask<>(key);
               lastKey = key;
               lastTask = debounced;
               executor.schedule(debounced, interval, intervalUnit);
            }
         }
         else {
            debounced = pending.computeIfAbsent(key, k -> {
               DebouncedTask<V> d = new DebouncedTask<>(k);
               executor.schedule(d, interval, intervalUnit);
               return d;
            });
         }

         debounced.defer(interval, intervalUnit, task, reducer);
         return debounced.future;
      }
      finally {
         lock.unlock();
      }
   }

   private <V> Callable<V> replace(Callable<V> previous, Callable<V> current) {
      return current;
   }

   private final ScheduledExecutorService executor;
   private final Map<T, DebouncedTask> pending = new HashMap<>();
   private T lastKey;
   private DebouncedTask<?> lastTask;
   private final Lock lock = new ReentrantLock();
   private final boolean preserveSequence;

   private final class DebouncedTask<V> implements Runnable {
      DebouncedTask(T key) {
         this.key = key;
      }

      void defer(long interval, TimeUnit intervalUnit, Callable<V> task,
                 BinaryOperator<Callable<V>> reducer)
      {
         lock.lock();

         try {
            if(scheduled >= 0) {
               long ms = TimeUnit.MILLISECONDS.convert(interval, intervalUnit);
               scheduled = System.currentTimeMillis() + ms;
               this.task = reducer.apply(this.task, task);
            }
         }
         finally {
            lock.unlock();
         }
      }

      @Override
      public void run() {
         lock.lock();

         try {
            long remaining = scheduled - System.currentTimeMillis();

            if(remaining > 0) {
               executor.schedule(this, remaining, TimeUnit.MILLISECONDS);
               return;
            }
            else {
               scheduled = -1L;

               if(preserveSequence) {
                  if(lastTask == this) {
                     lastKey = null;
                     lastTask = null;
                  }
               }
               else {
                  pending.remove(key);
               }
            }
         }
         finally {
            lock.unlock();
         }

         try {
            future.complete(task.call());
         }
         catch(Exception e) {
            future.completeExceptionally(e);
         }
      }

      private final T key;
      private Callable<V> task;
      private final CompletableFuture<V> future = new CompletableFuture<>();
      private long scheduled;
   }

   private static final class RunnableCallable implements Callable<Void> {
      RunnableCallable(Runnable task) {
         this.task = task;
      }

      @Override
      public Void call() {
         task.run();
         return null;
      }

      private final Runnable task;
   }

   private static final class RunnableReducer implements BinaryOperator<Callable<Void>> {
      RunnableReducer(BinaryOperator<Runnable> reducer) {
         this.reducer = reducer;
      }

      @Override
      public Callable<Void> apply(Callable<Void> previous, Callable<Void> current) {
         Runnable previousRunnable = previous == null ? null : ((RunnableCallable) previous).task;
         Runnable currentRunnable = current == null ? null : ((RunnableCallable) current).task;
         return new RunnableCallable(reducer.apply(previousRunnable, currentRunnable));
      }

      private final BinaryOperator<Runnable> reducer;
   }
}
