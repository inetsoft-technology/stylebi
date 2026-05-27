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

import com.google.common.collect.Comparators;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

final class PathService implements Comparator<CachePath> {
   public FileSystem getFileSystem() {
      return fileSystem;
   }

   public void setFileSystem(FileSystem fileSystem) {
      if(this.fileSystem != null) {
         throw new IllegalStateException("The file system is already set");
      }

      this.fileSystem = Objects.requireNonNull(fileSystem);
   }

   public String getSeparator() {
      return SEPARATOR;
   }

   public CachePath emptyPath() {
      CachePath result = emptyPath;

      if(result == null) {
         result = createPathInternal(null, List.of(Name.EMPTY));
         emptyPath = result;
      }

      return result;
   }

   public Name name(String name) {
      return switch(name) {
         case "" -> Name.EMPTY;
         case "." -> Name.SELF;
         case ".." -> Name.PARENT;
         default -> Name.create(name, name);
      };
   }

   private List<Name> names(Iterable<String> names) {
      List<Name> result = new ArrayList<>();

      for(String name : names) {
         result.add(name(name));
      }

      return result;
   }

   public CachePath createRoot(Name root) {
      return createPath(Objects.requireNonNull(root), List.of());
   }

   public CachePath createFileName(Name name) {
      return createPath(null, List.of(name));
   }

   public CachePath createRelativePath(Iterable<Name> names) {
      return createPath(null, names);
   }

   public CachePath createPath(Name root, Iterable<Name> names) {
      List<Name> nameList = new ArrayList<>();

      for(Name name : names) {
         if(!name.toString().isEmpty()) {
            nameList.add(name);
         }
      }

      if(root == null && nameList.isEmpty()) {
         return emptyPath();
      }

      return createPathInternal(root, nameList);
   }

   private CachePath createPathInternal(Name root, List<Name> names) {
      return new CachePath(this, root, names);
   }

   public CachePath parsePath(String first, String... more) {
      StringBuilder pathStr = new StringBuilder();

      if(!first.isEmpty()) {
         pathStr.append(first);
      }

      for(String part : more) {
         if(!part.isEmpty()) {
            if(!pathStr.isEmpty()) {
               pathStr.append(SEPARATOR);
            }

            pathStr.append(part);
         }
      }

      return parsePathString(pathStr.toString());
   }

   private CachePath parsePathString(String pathStr) {
      String root = null;
      List<String> names;

      if(pathStr.isEmpty()) {
         names = List.of("");
      }
      else {
         int nulIndex = pathStr.indexOf("\0");

         if(nulIndex != -1) {
            throw new InvalidPathException(pathStr, "nul character not allowed", nulIndex);
         }

         if(pathStr.charAt(0) == '/') {
            root = "/";
            names = Arrays.asList(pathStr.substring(1).split("/"));
         }
         else {
            names = Arrays.asList(pathStr.split("/"));
         }
      }

      return toPath(root, names);
   }

   private CachePath toPath(String root, List<String> names) {
      Name rootName = root == null ? null : name(root);
      Iterable<Name> nameList = names(names);
      return createPath(rootName, nameList);
   }

   public String toString(CachePath path) {
      Name root = path.root();
      String rootString = root == null ? null : root.toString();
      List<String> names = path.names().stream().map(String::valueOf).toList();

      StringBuilder result = new StringBuilder();

      if(rootString != null) {
         result.append(rootString);
      }

      result.append(String.join(SEPARATOR, names));
      return result.toString();
   }

   public int hash(CachePath path) {
      return Objects.hash(getFileSystem(), path.root(), path.names());
   }

   @Override
   public int compare(CachePath o1, CachePath o2) {
      return Comparator.comparing(CachePath::root, rootComparator)
         .thenComparing(CachePath::names, namesComparator)
         .compare(o1, o2);
   }

   public URI toUri(URI fileSystemUri, CachePath path) {
      if(!path.isAbsolute()) {
         throw new IllegalArgumentException("path " + path + " must be absolute");
      }

      String root = String.valueOf(path.root());
      List<String> names = path.names().stream().map(String::valueOf).toList();
      return toUri(fileSystemUri, root, names, Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
   }

   private URI toUri(URI fileSystemUri, String root, List<String> names, boolean directory) {
      String path = toUriPath(root, names, directory);

      try {
         return new URI(
            fileSystemUri.getScheme(), fileSystemUri.getUserInfo(), fileSystemUri.getHost(),
            fileSystemUri.getPort(), path, null, null);
      }
      catch(URISyntaxException e) {
         throw new AssertionError(e);
      }
   }

   private String toUriPath(String root, List<String> names, boolean directory) {
      StringBuilder result = new StringBuilder();

      if(root != null) {
         result.append(root);
      }

      for(String name : names) {
         result.append('/').append(name);
      }

      if(directory || result.isEmpty()) {
         result.append('/');
      }

      return result.toString();
   }

   public PathMatcher createPathMatcher(String syntaxAndPattern) {
      int pos = syntaxAndPattern.indexOf(':');

      if(pos <= 0) {
         throw new IllegalArgumentException();
      }

      String syntax = syntaxAndPattern.substring(0, pos);
      String pattern = syntaxAndPattern.substring(pos + 1);
      String expr;

      if(syntax.equalsIgnoreCase("glob")) {
         expr = Globs.toRegexPattern(pattern);
      }
      else if(syntax.equalsIgnoreCase("regex")) {
         expr = pattern;
      }
      else {
         throw new UnsupportedOperationException("Syntax " + syntax + " is not supported");
      }

      Pattern p = Pattern.compile(expr);
      return path -> p.matcher(p.toString()).matches();
   }

   private static final String SEPARATOR = "/";

   private static final Comparator<Name> rootComparator =
      Comparator.nullsLast(Name.displayComparator());
   private static final Comparator<Iterable<Name>> namesComparator =
      Comparators.lexicographical(Name.displayComparator());


   private volatile FileSystem fileSystem;
   private volatile CachePath emptyPath;
}
