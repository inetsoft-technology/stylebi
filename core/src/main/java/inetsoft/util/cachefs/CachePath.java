/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.util.cachefs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class CachePath implements Path {
   CachePath(PathService pathService, Name root, List<Name> names) {
      this.pathService = pathService;
      this.root = root;
      this.names = names;
   }

   Name root() {
      return root;
   }

   List<Name> names() {
      return names;
   }

   public boolean isEmptyPath() {
      return root == null && names.size() == 1 && names.getFirst().toString().isEmpty();
   }

   @Override
   public FileSystem getFileSystem() {
      return pathService.getFileSystem();
   }

   public CacheFileSystem getCacheFileSystem() {
      return (CacheFileSystem) pathService.getFileSystem();
   }

   @Override
   public boolean isAbsolute() {
      return root != null;
   }

   @Override
   public Path getRoot() {
      return root == null ? null : pathService.createRoot(root);
   }

   @Override
   public Path getFileName() {
      return names.isEmpty() ? null : getName(names.size() - 1);
   }

   @Override
   public Path getParent() {
      if(names.isEmpty() || (names.size() == 1 && root == null)) {
         return null;
      }

      return pathService.createPath(root, names.subList(0, names.size() - 1));
   }

   @Override
   public int getNameCount() {
      return names.size();
   }

   @Override
   public Path getName(int index) {
      if(index < 0 || index >= names.size()) {
         throw new IllegalArgumentException("Index out of bounds");
      }

      return pathService.createFileName(names.get(index));
   }

   @Override
   public Path subpath(int beginIndex, int endIndex) {
      if(beginIndex < 0 || endIndex >= names.size()) {
         throw new IllegalArgumentException("Index out of bounds");
      }

      if(endIndex <= beginIndex) {
         throw new IllegalArgumentException("End index must be greater than begin index");
      }

      return pathService.createRelativePath(names.subList(beginIndex, endIndex));
   }

   @Override
   public boolean startsWith(String other) {
      return startsWith(pathService.parsePath(other));
   }

   @Override
   public boolean startsWith(Path other) {
      CachePath otherPath = checkPath(other);
      return otherPath != null &&
              getFileSystem().equals(otherPath.getFileSystem()) &&
              Objects.equals(root, otherPath.root) &&
              startsWith(names, otherPath.names);
   }

   private boolean startsWith(List<?> list, List<?> other) {
      return list.size() >= other.size() && list.subList(0, other.size()).equals(other);
   }

   @Override
   public boolean endsWith(String other) {
      return endsWith(pathService.parsePath(other));
   }

   @Override
   public boolean endsWith(Path other) {
      CachePath otherPath = checkPath(other);

      if(otherPath == null) {
         return false;
      }

      if(otherPath.isAbsolute()) {
         return compareTo(otherPath) == 0;
      }

      return startsWith(names.reversed(), otherPath.names().reversed());
   }

   @Override
   public Path normalize() {
      if(isNormal()) {
         return this;
      }

      Deque<Name> newNames = new ArrayDeque<>();

      for(Name name : names) {
         if(name.equals(Name.PARENT)) {
            Name lastName = newNames.peekLast();

            if(lastName != null && !lastName.equals(Name.PARENT)) {
               newNames.removeLast();
            }
            else if(!isAbsolute()) {
               newNames.add(name);
            }
         }
         else if(!name.equals(Name.SELF)) {
            newNames.add(name);
         }
      }

      if(names.size() == newNames.size()) {
         boolean same = true;

         for(Iterator<Name> i1 = newNames.iterator(), i2 = names.iterator(); i1.hasNext() && i2.hasNext(); ) {
            if(!Objects.equals(i1.next(), i2.next())) {
               same = false;
               break;
            }
         }

         if(same) {
            return this;
         }
      }

      return pathService.createPath(root, newNames);
   }

   private boolean isNormal() {
      if(getNameCount() == 0 || (getNameCount() == 1 && !isAbsolute())) {
         return true;
      }

      boolean foundNonParentName = isAbsolute();
      boolean normal = true;

      for(Name name : names) {
         if(name.equals(Name.PARENT)) {
            if(foundNonParentName) {
               normal = false;
               break;
            }
         }
         else {
            if(name.equals(Name.SELF)) {
               normal = false;
               break;
            }

            foundNonParentName = true;
         }
      }

      return normal;
   }

   @Override
   public Path resolve(Path other) {
      CachePath otherPath = checkPath(other);

      if(otherPath == null) {
         throw new ProviderMismatchException(other.toString());
      }

      if(isEmptyPath() || otherPath.isEmptyPath()) {
         return otherPath;
      }

      if(otherPath.isEmptyPath()) {
         return this;
      }

      List<Name> newNames = new ArrayList<>(names);
      newNames.addAll(otherPath.names);
      return pathService.createPath(root, newNames);
   }

   @Override
   public Path resolve(String other) {
      return resolve(pathService.parsePath(other));
   }

   @Override
   public Path resolveSibling(Path other) {
      CachePath otherPath = checkPath(other);

      if(otherPath == null) {
         throw new ProviderMismatchException(other.toString());
      }

      if(otherPath.isAbsolute()) {
         return otherPath;
      }

      Path parent = getParent();

      if(parent == null) {
         return otherPath;
      }

      return parent.resolve(other);
   }

   @Override
   public Path resolveSibling(String other) {
      return resolveSibling(pathService.parsePath(other));
   }

   @Override
   public Path relativize(Path other) {
      CachePath otherPath = checkPath(other);

      if(otherPath == null) {
         throw new ProviderMismatchException(other.toString());
      }

      if(!Objects.equals(root, otherPath.root)) {
         throw new IllegalArgumentException("Relative paths must have the same root");
      }

      if(equals(other)) {
         return pathService.emptyPath();
      }

      if(isEmptyPath()) {
         return otherPath;
      }

      List<Name> otherNames = otherPath.names;
      int sharedSubsequenceLength = 0;

      for(int i = 0; i < Math.min(getNameCount(), otherNames.size()); i++) {
         if(names.get(i).equals(otherNames.get(i))) {
            sharedSubsequenceLength++;
         }
         else {
            break;
         }
      }

      int extraNamesInThis = Math.max(0, getNameCount() - sharedSubsequenceLength);
      List<Name> extraNamesInOther = otherNames.size() <= sharedSubsequenceLength ?
              List.of() : otherNames.subList(sharedSubsequenceLength, otherNames.size());

      List<Name> parts = new ArrayList<>(extraNamesInThis + extraNamesInOther.size());
      parts.addAll(Collections.nCopies(extraNamesInThis, Name.PARENT));
      parts.addAll(extraNamesInOther);

      return pathService.createRelativePath(parts);
   }

   @Override
   public URI toUri() {
      return getCacheFileSystem().toUri(this);
   }

   @Override
   public File toFile() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Path toAbsolutePath() {
      return isAbsolute() ? this : getCacheFileSystem().getWorkingDirectory().resolve(this);
   }

   @Override
   public Path toRealPath(LinkOption... options) {
      return toAbsolutePath();
   }

   @Override
   public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
           throws IOException
   {
      return register(watcher, events);
   }

   @Override
   public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
      if(!(watcher instanceof CacheWatchService cacheWatcher)) {
         throw new IllegalArgumentException("Watcher (" + watcher + ") is not associated with this file system");
      }

      return cacheWatcher.register(this, Arrays.asList(events));
   }

   @Override
   public Iterator<Path> iterator() {
      return asList().iterator();
   }

   private List<Path> asList() {
      return new AbstractList<>() {
          @Override
          public Path get(int index) {
              return getName(index);
          }

          @Override
          public int size() {
              return getNameCount();
          }
      };
   }

   @Override
   public int compareTo(Path other) {
      CachePath otherPath = checkPath(other);
      return Comparator.comparing((CachePath p) -> p.getCacheFileSystem().getUri())
              .thenComparing(pathService)
              .compare(this, otherPath);
   }

   @Override
   public boolean equals(Object other) {
      return other instanceof CachePath && compareTo((CachePath) other) == 0;
   }

   @Override
   public int hashCode() {
      return pathService.hash(this);
   }

   @Override
   public String toString() {
      return pathService.toString(this);
   }

   private CachePath checkPath(Path other) {
      if(Objects.requireNonNull(other) instanceof CachePath && other.getFileSystem().equals(getFileSystem())) {
         return (CachePath) other;
      }

      return null;
   }

   private final Name root;
   private final List<Name> names;
   private final PathService pathService;
}
