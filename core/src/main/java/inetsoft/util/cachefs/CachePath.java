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

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;

public class CachePath implements Path {
   @Override
   public FileSystem getFileSystem() {
      return null;
   }

   @Override
   public boolean isAbsolute() {
      return false;
   }

   @Override
   public Path getRoot() {
      return null;
   }

   @Override
   public Path getFileName() {
      return null;
   }

   @Override
   public Path getParent() {
      return null;
   }

   @Override
   public int getNameCount() {
      return 0;
   }

   @Override
   public Path getName(int index) {
      return null;
   }

   @Override
   public Path subpath(int beginIndex, int endIndex) {
      return null;
   }

   @Override
   public boolean startsWith(Path other) {
      return false;
   }

   @Override
   public boolean endsWith(Path other) {
      return false;
   }

   @Override
   public Path normalize() {
      return null;
   }

   @Override
   public Path resolve(Path other) {
      return null;
   }

   @Override
   public Path relativize(Path other) {
      return null;
   }

   @Override
   public URI toUri() {
      return null;
   }

   @Override
   public Path toAbsolutePath() {
      return null;
   }

   @Override
   public Path toRealPath(LinkOption... options) throws IOException {
      return null;
   }

   @Override
   public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
      return null;
   }

   @Override
   public int compareTo(Path other) {
      return 0;
   }

   @Override
   public boolean equals(Object other) {
      return false;
   }

   @Override
   public int hashCode() {
      return 0;
   }

   @Override
   public String toString() {
      return "";
   }
}
