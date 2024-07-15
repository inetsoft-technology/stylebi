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
package inetsoft.util.affinity;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;

/**
 * Implementation fo <tt>AffinitySupport</tt> for Windows platforms.
 */
class WindowsAffinitySupport implements AffinitySupport {
   @Override
   public int[] getAffinity() throws Exception {
      int[] result;

      Kernel32 library = Kernel32.INSTANCE;
      Handle process = library.GetCurrentProcess();
      IntByReference processMask = new IntByReference(0);
      IntByReference systemMask = new IntByReference(0);

      if(!library.GetProcessAffinityMask(process, processMask, systemMask)) {
         throw new Exception("Kernel32 GetProcessAffinityMask failed");
      }

      ArrayList<Integer> list = new ArrayList<>();
      int index = 0;
      int value = processMask.getValue();

      while(value != 0) {
         if(value % 2 != 0) {
            list.add(index);
         }

         ++index;
         value = value >>> 1;
      }

      result = new int[list.size()];

      for(int i = 0; i < list.size(); i++) {
         result[i] = list.get(i);
      }

      return result;
   }

   @Override
   public void setAffinity(int[] processors) throws Exception {
      Kernel32 library = Kernel32.INSTANCE;
      Handle process = library.GetCurrentProcess();
      int affinity = 0;

      for(int processor : processors) {
         affinity |= (1 << processor);
      }

      if(!library.SetProcessAffinityMask(process, affinity)) {
         throw new Exception("Kernel32 SetProcessAffinityMask failed");
      }
   }

   private static final Handle INVALID_HANDLE_VALUE =  new Handle(
      Pointer.createConstant(Native.POINTER_SIZE == 8 ? -1 : 0xFFFFFFFFL));

   /**
    * Class that represents a Windows HANDLE type.
    */
   public static class Handle extends PointerType {
      /**
       * Creates a new instance of <tt>Handle</tt>.
       */
      public Handle() {
      }

      /**
       * Creates a new instance of <tt>Handle</tt>.
       *
       * @param p the wrapped pointer.
       */
      public Handle(Pointer p) {
         setPointer(p);
         immutable = true;
      }

      @Override
      public Object fromNative(Object nativeValue, FromNativeContext context) {
         Object o = super.fromNative(nativeValue, context);

         if(INVALID_HANDLE_VALUE.equals(o)) {
            return INVALID_HANDLE_VALUE;
         }

         return o;
      }

      @Override
      public void setPointer(Pointer p) {
         if(immutable) {
            throw new IllegalStateException("Handle is immutable");
         }

         super.setPointer(p);
      }

      private boolean immutable;
   }

   /**
    * Native interface for the functions in the Kernel32 library required for
    * CPU affinity support.
    */
   public interface Kernel32 extends Library {
      /**
       * Gets the handle for the current process.
       *
       * @return the current process handle.
       */
      Handle GetCurrentProcess();

      /**
       * Retrieves the process affinity mask for the specified process and the
       * system affinity mask for the system.
       *
       * @param hProcess              a handle to the process whose affinity
       *                              mask is desired.
       * @param lpProcessAffinityMask a pointer to the variable that receives
       *                              the affinity mask for the specified
       *                              process.
       * @param systemAffinityMask    a pointer to a variable that receives the
       *                              affinity mask for the system.
       *
       * @return <tt>true</tt> if successful; <tt>false</tt> otherwise.
       */
      boolean GetProcessAffinityMask(Handle hProcess,
                                     IntByReference lpProcessAffinityMask,
                                     IntByReference systemAffinityMask);

      /**
       * Sets a processor affinity mask for the threads of the specified
       * process.
       *
       * @param hprocess              a handle to the process whose affinity
       *                              mask is to be set.
       * @param dwProcessAffinityMask the affinity mask for the threads of the
       *                              process.
       *
       * @return <tt>true</tt> if successful; <tt>false</tt> otherwise.
       */
      boolean SetProcessAffinityMask(Handle hprocess,
                                     int dwProcessAffinityMask);

      /**
       * The shared native library instance.
       */
      Kernel32 INSTANCE = Native.load(
         "kernel32", Kernel32.class, W32APIOptions.UNICODE_OPTIONS);
   }
}
