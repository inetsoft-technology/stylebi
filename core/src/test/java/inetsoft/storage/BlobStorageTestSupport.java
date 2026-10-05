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
package inetsoft.storage;

import inetsoft.util.DataSpace;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test support for the {@link BlobStorage} change events.
 */
public final class BlobStorageTestSupport {
   private BlobStorageTestSupport() {
   }

   /**
    * Bugs #77759, #77760. Waits until the closed blob storages have run the change events that
    * were queued on their BlobStorageEvent threads when they were closed. Called at a Spring
    * context boundary, before the next context is installed in the {@code ConfigurationContext}:
    * a listener of a closed context that looks up a bean when an event arrives, e.g.
    * {@code DataSpace.getDataSpace()}, would otherwise resolve it in the next context, and create
    * that context's beans on the event thread before its bean post-processors are registered.
    *
    * @return {@code null} if the events have run, or a message with the stacks of the
    *         BlobStorageEvent threads if they did not run within the timeout. They are not
    *         waited for again after a timeout.
    */
   public static String awaitEventBarrier() {
      long millis = timeoutMillis;

      try {
         if(BlobStorage.awaitClosedEventExecutors(millis, TimeUnit.MILLISECONDS)) {
            return null;
         }
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
         return "Interrupted while waiting for the events of the closed blob storages";
      }

      StringBuilder message = new StringBuilder()
         .append("The events queued on the BlobStorageEvent threads of the closed blob storages ")
         .append("did not run within ").append(millis).append(" ms. BlobStorageEvent threads:");

      for(Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
         Thread thread = entry.getKey();

         if("BlobStorageEvent".equals(thread.getName())) {
            message.append("\n\"").append(thread.getName()).append("\" Id=")
               .append(thread.threadId()).append(' ').append(thread.getState());

            for(StackTraceElement element : entry.getValue()) {
               message.append("\n\tat ").append(element);
            }
         }
      }

      return message.toString();
   }

   /**
    * Sets the time that {@link #awaitEventBarrier()} waits, for a test of the timeout.
    */
   public static void setEventBarrierTimeout(long timeout, TimeUnit unit) {
      timeoutMillis = unit.toMillis(timeout);
   }

   /**
    * Restores the default time that {@link #awaitEventBarrier()} waits.
    */
   public static void resetEventBarrierTimeout() {
      timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
   }

   /**
    * Adds a listener to the key-value storage of the data space's blob storage that is called
    * after the blob storage's own listener, which queues the change on the BlobStorageEvent
    * thread. Both are called one after the other, in hash code order, by one OnDemand thread, so
    * the latch is counted down once the change of the key is queued.
    *
    * @param dataSpace the data space.
    * @param key       the key of the file in the key-value storage, see
    *                  {@link DataSpace#getPath(String, String)}.
    * @param queued    the latch to count down.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public static void addLastListener(DataSpace dataSpace, String key, CountDownLatch queued)
      throws ReflectiveOperationException
   {
      Field field = DataSpace.class.getDeclaredField("blobStorage");
      field.setAccessible(true);
      KeyValueStorage storage = ((BlobStorage<?>) field.get(dataSpace)).getStorage();

      storage.addListener(new KeyValueStorage.Listener() {
         @Override
         public void entryAdded(KeyValueStorage.Event event) {
            entryUpdated(event);
         }

         @Override
         public void entryUpdated(KeyValueStorage.Event event) {
            if(key.equals(event.getKey())) {
               queued.countDown();
            }
         }

         @Override
         public void entryRemoved(KeyValueStorage.Event event) {
         }

         @Override
         public int hashCode() {
            return Integer.MAX_VALUE;
         }
      });
   }

   private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);
   private static volatile long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
}
