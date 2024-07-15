/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * A list that pages its objects to file. The values can only be accessed
 * sequentially through an iterator.
 */
public class PagedSeqList<T> {
   /**
    * Create a paged list.
    * @param threshold number of entries to keep in memory before paging.
    */
   public PagedSeqList(int threshold) {
      this(threshold, null);
   }

   public PagedSeqList(int threshold, Comparator<? super T> comparator) {
      this.threshold = threshold;
      this.comparator = comparator;
   }

   /**
    * Add a value to the end of the list.
    */
   public synchronized void add(T val) {
      if(completed) {
         throw new RuntimeException("PagedSeqList is accessed after complete is called");
      }

      list.add(val);

      if(list.size() >= threshold) {
         swap();
      }
   }

   private boolean isSorted() {
      return comparator != null;
   }

   /**
    * Get the number of entries.
    */
   public int size() {
      return list.size() + pagedCnt;
   }

   /**
    * This function must be called when the list is completed.
    */
   public synchronized void complete() {
      if(completed) {
         return;
      }

      completed = true;

      if(isSorted()) {
         list.sort(comparator);

         if(pages != null) {
            mergeSortedPages();
            pages = null;
         }
      }

      if(output != null) {
         try {
            output.close();
            output = null;
         }
         catch(Exception ex) {
            LOG.error("Failed to close output stream", ex);
         }
      }
   }

   /**
    * Get an iterator. The iterator can only be called after the list is
    * completed.
    */
   public Iterator<T> iterator() {
      if(!completed) {
         throw new IllegalStateException(
            "This list must be completed before calling the iterator.");
      }

      return new PagedSeqListIterator();
   }

   /**
    * This method should be called when the list is no longer needed.
    */
   public void dispose() {
      complete();

      if(pages != null) {
         for(File page : pages) {
            if(!page.delete()) {
               FileSystemService.getInstance().remove(page, 60000);
            }
         }

         pages = null;
      }

      if(swapfile != null) {
         boolean removed = swapfile.delete();

         if(!removed) {
            FileSystemService.getInstance().remove(swapfile, 60000);
         }

         swapfile = null;
      }
   }

   public Comparator<? super T> getComparator() {
      return comparator;
   }

   public boolean hasPagedEntries() {
      return pagedCnt > 0;
   }

   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Restore a value read back from serialized file.
    */
   protected T restore(T val) {
      return val;
   }

   private void swap() {
      if(isSorted()) {
         swapSorted();
      }
      else {
         swapUnsorted();
      }
   }

   /**
    * Swap unpaged entries to file.
    */
   private synchronized void swapUnsorted() {
      try {
         if(swapfile == null) {
            swapfile = FileSystemService.getInstance().getCacheTempFile("pagedseqlist", "dat");
            output = createOutputStream(swapfile);
         }

         if(output == null) {
            throw new RuntimeException("PagedSeqList is accessed after complete is called");
         }

         for(int i = 0; i < list.size(); i++) {
            writeToOutput(output, list.get(i));
            resetOutput(output, i + 1);
         }

         pagedCnt += list.size();
         list.clear();
         output.flush();
         output.reset();
      }
      catch(Exception ex) {
         LOG.error("Failed to write swap file: " + swapfile, ex);
      }
   }

   /**
    * Swap unpaged entries to file.
    */
   private synchronized void swapSorted() {
      list.sort(comparator);

      final File pageFile = FileSystemService.getInstance()
         .getCacheTempFile("pagedseqlist", "dat");

      if(pages == null) {
         pages = new ArrayList<>();
      }

      pages.add(pageFile);

      try(ObjectOutputStream output = createOutputStream(pageFile)) {
         for(T obj : list) {
            try {
               output.writeUnshared(obj);
            }
            catch(Exception e) {
               LOG.debug("Failed to write object: " + obj, e);
            }
         }

         pagedCnt += list.size();
         list.clear();
      }
      catch(Exception ex) {
         LOG.error("Failed to write page file: " + pageFile, ex);
      }
   }

   private void mergeSortedPages() {
      swapfile = FileSystemService.getInstance().getCacheTempFile("pagedseqlist", "dat");
      final List<ObjectInputStream> pageInputs = new ArrayList<>(pages.size());
      Plugin plugin = Plugins.getInstance().getPlugin("inetsoft.xmlformats");

      if(plugin != null) {
         ClassLoader loader = plugin.getClassLoader();
         Thread.currentThread().setContextClassLoader(loader);
      }

      try(ObjectOutputStream output = createOutputStream(swapfile)) {
         for(File page : pages) {
            pageInputs.add(createInputStream(page));
         }

         final ArrayList<Iterator<T>> iterators = new ArrayList<>(pages.size() + 1);

         for(ObjectInputStream pageInput : pageInputs) {
            iterators.add(new ObjectInputStreamIterator(pageInput, threshold));
         }

         iterators.add(list.iterator());

         final ArrayList<T> initial = new ArrayList<>(pageInputs.size() + 1);

         for(Iterator<T> iterator : iterators) {
            if(iterator.hasNext()) {
               initial.add(iterator.next());
            }
         }

         final TournamentTree<T> tournamentTree = new TournamentTree<>(initial, comparator);
         int winnerIdx;

         for(int writeCount = 1; (winnerIdx = tournamentTree.getWinnerIndex()) != -1; writeCount++)
         {
            final T winner = tournamentTree.getWinner();
            writeToOutput(output, winner);
            resetOutput(output, writeCount);

            final Iterator<T> iterator = iterators.get(winnerIdx);
            final T nextValAtIdx;

            if(iterator.hasNext()) {
               nextValAtIdx = iterator.next();
            }
            else {
               nextValAtIdx = null;
            }

            tournamentTree.replaceValueAtIndex(winnerIdx, nextValAtIdx);
         }

         pagedCnt += list.size();
         list.clear();
      }
      catch(IOException ex) {
         LOG.error("Failed to merge sorted pages.", ex);
      }
      finally {
         for(ObjectInputStream pageInput : pageInputs) {
            IOUtils.closeQuietly(pageInput);
         }
      }
   }

   private ObjectOutputStream createOutputStream(File file) {
      try {
         return new ObjectOutputStream(Tool.createCompressOutputStream(
            new BufferedOutputStream(new FileOutputStream(file))));
      }
      catch(IOException ex) {
         LOG.error("Failed to create output stream for file: {}", file, ex);
         return null;
      }
   }

   private ObjectInputStream createInputStream(File file) {
      try {
         return new ObjectInputStream(Tool.createUncompressInputStream(
            new BufferedInputStream(new FileInputStream(file))))
         {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
               throws IOException, ClassNotFoundException
            {
               String name = desc.getName();

               try {
                  ClassLoader loader = Thread.currentThread().getContextClassLoader();

                  return Class.forName(name, false, loader);
               }
               catch(ClassNotFoundException ex) {
                  return super.resolveClass(desc);
               }
            }
         };
      }
      catch(IOException ex) {
         LOG.error("Failed to create input stream for file: {}", file, ex);
         return null;
      }
   }

   private void writeToOutput(ObjectOutputStream output, T obj) {
      try {
         output.writeUnshared(obj);
      }
      catch(Exception e) {
         LOG.debug("Failed to write object: " + obj, e);
      }
   }

   private void resetOutput(ObjectOutputStream output, int writeCount) throws IOException {
      if(writeCount % 1000 == 0) { // cap memory of output stream
         output.reset();
      }
   }

   private final class ObjectInputStreamIterator implements Iterator<T> {
      ObjectInputStreamIterator(ObjectInputStream input, int total) {
         this.total = total;
         this.input = input;
      }

      @Override
      public boolean hasNext() {
         return readCount < total;
      }

      @Override
      public T next() {
         if(!hasNext()) {
            return null;
         }

         readCount++;

         try {
            return restore((T) input.readUnshared());
         }
         catch(Exception ex) {
            LOG.error("Failed to read from page file.");
            return null;
         }
      }

      private int readCount = 0;

      private final ObjectInputStream input;
      private final int total;
   }

   class PagedSeqListIterator implements Iterator<T> {
      /**
       * Check if there are more entries.
       */
      @Override
      public boolean hasNext() {
         synchronized(PagedSeqList.this) {
            final int seqListSize = pagedCnt + list.size();

            for(int i = idx + 1; i < seqListSize; i++) {
               if(!removed.get(i)) {
                  return true;
               }
            }
         }

         if(input != null) {
            try {
               input.close();
            }
            catch(Exception ex) {
               LOG.error("Failed to close input stream", ex);
            }
         }

         return false;
      }

      /**
       * Return the next entry.
       */
      @Override
      public T next() {
         idx++;

         synchronized(PagedSeqList.this) {
            if(idx >= pagedCnt) {
               return list.get(idx - pagedCnt);
            }

            try {
               if(input == null) {
                  input = createInputStream(swapfile);
               }

               // skip removed items
               for(; removed.get(idx); idx++) {
                  input.readUnshared();
               }

               return restore((T) input.readUnshared());
            }
            catch(Throwable ex) {
               LOG.error("Failed to restore from swap file: " + swapfile, ex);
               return null;
            }
         }
      }

      /**
       * Remove the current entry.
       */
      @Override
      public void remove() {
         if(idx >= 0) {
            removed.set(idx);
         }
      }

      @Override
      protected void finalize() throws Throwable {
         if(input != null) {
            try {
               input.close();
            }
            catch(Exception ex) {
               // ignore it
            }
         }

         super.finalize();
      }

      private int idx = -1;
      private ObjectInputStream input;
   }

   private boolean completed;
   private int pagedCnt = 0; // number of entries paged out
   private List<File> pages;
   private File swapfile;
   private ObjectOutputStream output;

   private final int threshold; // number of entries before paging starts
   private final List<T> list = new ArrayList<>();
   private final BitSet removed = new BitSet(); // removed entry index
   private final Comparator<? super T> comparator; // for sorting

   private static final Logger LOG = LoggerFactory.getLogger(PagedSeqList.class);
}
