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
package inetsoft.util.affinity;

import com.sun.jna.*;
import com.sun.jna.ptr.NativeLongByReference;

import java.util.ArrayList;

/**
 * Implementation of <tt>AffinitySupport</tt> for Posix platforms.
 */
class PosixAffinitySupport implements AffinitySupport {
   @Override
   public int[] getAffinity() throws Exception {
      int[] result;
      NativeLongByReference mask = new NativeLongByReference();

      if(CLibrary.INSTANCE.sched_getaffinity(CLibrary.INSTANCE.getpid(),
                                             NativeLong.SIZE, mask) != 0)
      {
         throw new Exception("Call to sched_getaffinity failed");
      }

      ArrayList<Integer> list = new ArrayList<>();
      int index = 0;
      long value = mask.getValue().longValue();

      while(value != 0L) {
         if(value % 2L != 0L) {
            list.add(index);
         }

         ++index;
         value = value >>> 1L;
      }

      result = new int[list.size()];

      for(int i = 0; i < list.size(); i++) {
         result[i] = list.get(i);
      }

      return result;
   }

   @Override
   public void setAffinity(int[] processors) throws Exception {
      setAffinity(processors, CLibrary.INSTANCE.getpid());
   }

   @Override
   public void setThreadAffinity(int[] processors) throws Exception {
      setAffinity(processors, 0);
   }

   private void setAffinity(int[] processors, int pid) throws Exception {
      long affinity = 0;

      for(int processor : processors) {
         affinity |= (1L << processor);
      }

      NativeLongByReference mask = new NativeLongByReference(new NativeLong(affinity));

      if(CLibrary.INSTANCE.sched_setaffinity(pid, NativeLong.SIZE, mask) != 0) {
         throw new Exception("Call to sched_setaffinity failed");
      }
   }

   /**
    * Native interface for the functions in the standard-C library required for
    * CPU affinity support.
    */
   public interface CLibrary extends Library {
      /**
       * Gets the affinity mask of the specified process.
       *
       * @param pid        the ID of the process. If zero, the current process
       *                   is used.
       * @param cpusetsize the size of the structure pointed to by mask.
       * @param mask       a pointer to the structure into which the affinity
       *                   mask will be written.
       *
       * @return 0 on success or -1 on error.
       */
      int sched_getaffinity(int pid, int cpusetsize, NativeLongByReference mask)
         throws LastErrorException;

      /**
       * Sets the affinity mask of the specified process.
       *
       * @param pid        the ID of the process. If zero, the current process
       *                   is used.
       * @param cpusetsize the size of the structure pointed to by mask.
       * @param mask       a pointer to the structure into which the affinity
       *                   mask will be written.
       *
       * @return 0 on success or -1 on error.
       */
      int sched_setaffinity(int pid, int cpusetsize, NativeLongByReference mask)
         throws LastErrorException;

      /**
       * Gets the process ID of the calling process.
       */
      int getpid();

      /**
       * The shared instance of the native library interface.
       */
      CLibrary INSTANCE = Native.load("c", CLibrary.class);
   }
}
