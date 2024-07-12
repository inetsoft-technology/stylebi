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

import java.util.concurrent.*;
import java.util.function.BinaryOperator;

/**
 * Interface for classes that handle debouncing a type of task.
 *
 * @param <T> the type of key used to identify duplicate tasks.
 */
public interface Debouncer<T> extends AutoCloseable {
   /**
    * Debounces a task so that only the most recent task with the same key submitted in the current
    * interval is executed.
    *
    * @param key          the key that identifies the task.
    * @param interval     the amount of time to wait before performing the task.
    * @param intervalUnit the unit of time for the interval.
    * @param task         the task to run.
    */
   void debounce(T key, long interval, TimeUnit intervalUnit, Runnable task);

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
   void debounce(T key, long interval, TimeUnit intervalUnit, Runnable task,
                 BinaryOperator<Runnable> reducer);

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
   <V> Future<V> debounce(T key, long interval, TimeUnit intervalUnit, Callable<V> task) ;

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
   <V> Future<V> debounce(T key, long interval, TimeUnit intervalUnit, Callable<V> task,
                          BinaryOperator<Callable<V>> reducer);
}
