/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UpgradableReadWriteLock {
   /**
    * Acquire a write (exclusive) lock.
    */
   public void lockWrite() {
      OptionalInt maxLevel = getStack().stream().mapToInt(Integer::intValue).max();

      // rewind all read lock and try to lock write lock
      if(maxLevel.isPresent() && maxLevel.getAsInt() < 1) {
         int cnt = getStack().size();

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().unlock();
         }
      }

      getStack().push(1);
      thisLock.writeLock().lock();
   }

   /**
    * Release a write (exclusive) lock.
    */
   public void unlockWrite() {
      pop();
      thisLock.writeLock().unlock();

      OptionalInt maxLevel = getStack().stream().mapToInt(Integer::intValue).max();

      if(maxLevel.isPresent() && maxLevel.getAsInt() < 1) {
         int cnt = getStack().size();

         for(int i = 0; i < cnt; i++) {
            thisLock.readLock().lock();
         }
      }
   }

   /**
    * Acquire a read (shared) lock.
    */
   public void lockRead() {
      thisLock.readLock().lock();
      getStack().push(0);
   }

   /**
    * Release a read (shared) lock.
    */
   public void unlockRead() {
      pop();
      thisLock.readLock().unlock();
   }

   /**
    * Unlock all currently locked locks, and stored the current lock states to be restored
    * in restoreLocks().
    */
   public void unlockAll() {
      List<Integer> olocks = thisOldLocks.get();
      Stack<Integer> stack = (Stack<Integer>) getStack().clone();
      olocks.clear();

      while(!stack.empty()) {
         Integer op = stack.pop();
         olocks.add(op);

         switch(op) {
         case 0:
            unlockRead();
            break;
         case 1:
            unlockWrite();
            break;
         }
      }
   }

   /**
    * Restore the locks unlocked in unlockAll.
    */
   public void restoreLocks() {
      List<Integer> olocks = thisOldLocks.get();

      for(int i = olocks.size() - 1; i >= 0; i--) {
         switch(olocks.get(i)) {
         case 0:
            lockRead();
            break;
         case 1:
            lockWrite();
            break;
         }
      }

      thisOldLocks.remove();
   }

   private int pop() {
      Stack<Integer> stack = getStack();
      int state = getStack().pop();

      if(stack.size() == 0) {
         thisLockState.get().remove(this);
      }

      return state;
   }

   private Stack<Integer> getStack() {
      Stack<Integer> stack = thisLockState.get().get(this);

      if(stack == null) {
         thisLockState.get().put(this, stack = new Stack<>());
      }

      return stack;
   }

   private final ReadWriteLock thisLock = new ReentrantReadWriteLock(false);
   private static final ThreadLocal<List<Integer>> thisOldLocks =
      ThreadLocal.withInitial(ArrayList::new);
   private static final ThreadLocal<Map<Object,Stack<Integer>>> thisLockState =
      ThreadLocal.withInitial(HashMap::new);
}
