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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * This class manages swapping of objects.
 *
 * @version 7.0, 3/20/2003
 * @author InetSoft Technology Corp
 */
public class ObjectSwapper {
   /**
    * Get the global object swapper.
    */
   public static synchronized ObjectSwapper getObjectSwapper() {
      if(objectSwapper == null) {
         objectSwapper = new ObjectSwapper();
      }

      return objectSwapper;
   }

   /**
    * Create an object swapper.
    */
   private ObjectSwapper() {
      TimedQueue.add(new Swapper());
   }

   /**
    * Add an object to be managed by the swapper.
    */
   public synchronized SwapKey add(SwappableObject obj) {
      SwapKey key = new SwapKey();

      if(obj instanceof ManagedObject) {
         pending.put(key, new ManagedSwapEntry((ManagedObject) obj));
      }
      else if(obj instanceof UnmanagedObject) {
         pending.put(key, new UnmanagedSwapEntry((UnmanagedObject) obj));
      }

      return key;
   }

   /**
    * Remove a swapped object from the pool.
    */
   public synchronized void remove(SwapKey key) {
      pending.remove(key);
      SwapEntry entry = swapped.remove(key);

      if(entry instanceof ManagedSwapEntry) {
         ((ManagedSwapEntry) entry).delete();
      }
   }

   /**
    * Swap back the object data. This method should only be called for
    * managed swappable objects.
    */
   public synchronized void restore(SwapKey key) {
      SwapEntry entry = swapped.get(key);

      if(entry instanceof ManagedSwapEntry) {
         if(((ManagedSwapEntry) entry).restore()) {
            swapped.remove(key);
            pending.put(key, entry);
         }
         else {
            LOG.warn("Restoring swapped object failed: " +
		    key + " " + entry);
         }
      }
      else {
         LOG.warn("Restore ignored, entry missing: " +
                 key + entry);
      }
   }

   /**
    * This interface should be implemented by the object to handle swapping
    * related operations.
    */
   public interface SwappableObject {
   }

   /**
    * This interface should be implemented by the object to handle swapping
    * related operations. The object should manage its own swapping. The
    * swapper will just call swap() whenever the object is idle for the
    * predefined period.
    */
   public interface UnmanagedObject extends SwappableObject {
      /**
       * This method is called to clear the memory of the object.
       * @return true if swapped successfully.
       */
      boolean swap();
   }

   /**
    * This interface should be implemented by the object to handle swapping
    * related operations. The object should provide the methods for serializing
    * and deserializing the data. The swapper will manage the swap file.
    */
   public interface ManagedObject extends SwappableObject {
      /**
       * This method is called to write the object data to a file.
       */
      void write(ObjectOutputStream output) throws IOException;

      /**
       * This method is called to read the object data from a file.
       */
      void read(ObjectInputStream input) throws IOException;

      /**
       * This method is called to clear the memory of the object.
       */
      void clear();
   }

   /**
    * The swap key is returned by the swapper when a swappable object is added
    * to the swap pool.
    */
   public static class SwapKey {
      /**
       * Touch the key when the object is accessed. It prevents the object from
       * being swapped to the disk.
       */
      public void touch() {
         beat++;
      }

      /**
       * Check if the object should be swapped.
       */
      boolean check() {
         boolean swap = beat == lastBeat;

         lastBeat = beat;
         return swap;
      }

      private int beat = 0; // current beat
      private int lastBeat = 0; // last checked beat
   }

   /**
    * SwapEntry holds the swapping information.
    */
   private abstract static class SwapEntry {
      /**
       * Swap the object to file.
       * @return true if swap is successful.
       */
      public abstract boolean swap();
   }

   /**
    * SwapEntry holds the swapping information.
    */
   private static class UnmanagedSwapEntry extends SwapEntry {
      public UnmanagedSwapEntry(UnmanagedObject obj) {
         this.ref = new WeakReference(obj);
      }

      /**
       * Swap the object to file.
       */
      @Override
      public boolean swap() {
	 UnmanagedObject obj = (UnmanagedObject) ref.get();
         return (obj != null) ? obj.swap() : false;
      }

      private Reference ref; // swappable object reference
   }

   /**
    * SwapEntry holds the swapping information.
    */
   private static class ManagedSwapEntry extends SwapEntry {
      public ManagedSwapEntry(ManagedObject obj) {
         this.ref = new WeakReference(obj);
      }

      /**
       * Swap the object to file.
       */
      @Override
      public boolean swap() {
         ManagedObject obj = (ManagedObject) ref.get();

         if(failed || obj == null) {
            return false;
         }

	 // @by larryl, always lock the obj instead of self to avoid deadlock
	 synchronized(obj) {
	    if(swapfile == null) {
	       swapfile = FileSystemService.getInstance().getCacheTempFile("objswap", "dat");

	       try {
                  ObjectOutputStream output = new ObjectOutputStream(
                     new BufferedOutputStream(new FileOutputStream(swapfile)));
                  obj.write(output);
                  obj.clear();
                  
                  output.close();
	       }
	       catch(Exception ex) {
                  LOG.error("Failed to write wrap file: " + 
                              swapfile, ex);
                  failed = true;
	       }
	    }
	    else {
	       // already swapped to file, just clear the memory
	       obj.clear();
	    }
	 }

         return !failed;
      }

      /**
       * Read back object data from file.
       */
      public boolean restore() {
	 ManagedObject obj = (ManagedObject) ref.get();

	 if(obj == null) {
            return false;
         }

         synchronized(obj) {
            if(!failed && swapfile != null && swapfile.exists()) {
               try {
                  ObjectInputStream input = new ObjectInputStream(
                     new BufferedInputStream(new FileInputStream(swapfile)));

                  obj.read(input);
                  input.close();
                  return true;
               }
               catch(Exception ex) {
                  LOG.error("Failed to read swap file: " +
                              swapfile, ex);
               }
            }
         }

         return false;
      }

      /**
       * Delete the file if already created.
       */
      public void delete() {
	 ManagedObject obj = (ManagedObject) ref.get();

	 if(obj == null) {
            if(swapfile != null) {
               boolean removed = swapfile.delete();

               if(!removed) {
                  FileSystemService.getInstance().remove(swapfile, 60000);
               }

               swapfile = null;
            }
         }
         else {
            synchronized(obj) {
               if(swapfile != null) {
                  boolean removed = swapfile.delete();

                  if(!removed) {
                     FileSystemService.getInstance().remove(swapfile, 60000);
                  }

                  swapfile = null;
               }
            }
         }
      }

      /**
       * Clear up the file if garbage collected.
       */
      @Override
      public void finalize() {
         delete();
      }

      private File swapfile = null;
      private Reference ref; // swappable object weak reference
      private boolean failed = false;
   }

   /**
    * Task for invoking swapper.
    */
   class Swapper extends TimedQueue.TimedRunnable {
      public Swapper() {
         super(30000);
      }

      @Override
      public boolean isRecurring() {
         return true;
      }

      @Override
      public void run() {
         List<SwapKey> plist = new ArrayList<>();

         // find all entries to be swapped
         synchronized(ObjectSwapper.this) {
            Iterator iter = pending.keySet().iterator();

            while(iter.hasNext()) {
               SwapKey key = (SwapKey) iter.next();

               if(key != null && key.check()) {
                  plist.add(key);
               }
            }
         }

         // perform the swapping outside of the synchronized block
         for(int i = 0; i < plist.size(); i++) {
            SwapKey key = plist.get(i);
            SwapEntry entry = pending.get(key);

            // the entry may have been removed if the object is destroyed
            if(entry != null) {
               synchronized(ObjectSwapper.this) {
                  swapped.put(key, entry);
                  pending.remove(key);
               }

               // the swap() must be called after the entry has been added to
               // swapped. Otherwise the restore() may be called before the
               // entry is called and causes the restore to fail.
               // entry.swap should be called outside of synchronized since
               // the locking sequence should always be
               // SwappableObject->ObjectSwapper
               // since swap and restore are both synchronized in entry, it
               // should not cause racing condition
               entry.swap();
            }
         }
      }
   }

   private static ObjectSwapper objectSwapper; // singleton
   private Map<SwapKey, SwapEntry> pending = new WeakHashMap<>(); // SwapKey -> SwappableObject
   private Map<SwapKey, SwapEntry> swapped = new WeakHashMap<>(); // SwapKey -> SwapEntry

   private static final Logger LOG =
      LoggerFactory.getLogger(ObjectSwapper.class);
}
