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
package inetsoft.uql.tabular;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;
import java.util.function.*;

/**
 * Bug #64331, decides which server directories a user may browse or save as the path of a
 * tabular data source, such as the root folder of a Text/Excel Directory data source. A site
 * admin, or anyone when security is disabled, may use any path. Anyone else, an organization
 * admin on a multi-tenant server included, may only use a path under one of the directories
 * listed in the {@value #ALLOWED_ROOTS_PROPERTY} property. With no directory listed such a user
 * may not browse the server or set a path at all.
 * <p>
 * The property is a list of absolute directories separated by commas or semicolons. It is
 * read for the user's organization (<code>inetsoft.org.&lt;org&gt;.data.file.allowed.roots</code>)
 * and falls back to the global value. Only a site admin can write it (EM Settings > Properties).
 * <p>
 * The allowed directories are checked when a path is browsed or saved, never when a saved data
 * source is queried, so a data source that was saved before with a root folder keeps working.
 * At query time the paths of a query are kept in the root folder of its data source (see
 * {@link #isUnderFolder(Path, Path)}): a path outside it is refused, and a data source without
 * a root folder reads no files.
 */
public final class ServerFilePathPolicy {
   /**
    * The property that lists the directories that a user who is not a site admin may use.
    */
   public static final String ALLOWED_ROOTS_PROPERTY = "data.file.allowed.roots";

   public ServerFilePathPolicy(BooleanSupplier securityEnabled, Predicate<Principal> siteAdmin,
                               Function<Principal, String> allowedRoots)
   {
      this.securityEnabled = securityEnabled;
      this.siteAdmin = siteAdmin;
      this.allowedRoots = allowedRoots;
   }

   /**
    * Gets the policy that uses the security engine and the configured allowed roots.
    */
   public static ServerFilePathPolicy getInstance() {
      return create(null);
   }

   /**
    * Gets the policy that uses a security engine and the configured allowed roots.
    *
    * @param securityEngine the security engine, {@code null} to use the default one.
    */
   public static ServerFilePathPolicy create(SecurityEngine securityEngine) {
      return new ServerFilePathPolicy(
         () -> (securityEngine != null ? securityEngine : SecurityEngine.getSecurity())
            .isSecurityEnabled(),
         principal -> OrganizationManager.getInstance().isSiteAdmin(principal),
         ServerFilePathPolicy::getAllowedRootsProperty);
   }

   /**
    * Determines if a user may browse and use any server path, that is when the user is a site
    * admin or security is disabled. The same rule as ScheduleTaskIdentityChecker.isUnrestricted.
    */
   public boolean isUnrestricted(Principal principal) {
      return !securityEnabled.getAsBoolean() || siteAdmin.test(principal);
   }

   /**
    * Gets the directories that a user who is not unrestricted may use.
    *
    * @return the absolute, normalized directories, empty if none is configured.
    */
   public List<Path> getAllowedRoots(Principal principal) {
      return parseRoots(allowedRoots.apply(principal));
   }

   /**
    * Determines if a user may browse or use a server path.
    */
   public boolean isAllowed(File file, Principal principal) {
      return isUnrestricted(principal) || isUnderAnyRoot(file, getAllowedRoots(principal));
   }

   /**
    * Throws an exception if a user may not save a data source because it sets a server path
    * that the user may not use. A path that is the same as the one of the stored data source is
    * always allowed, so an existing data source can still be renamed and edited.
    *
    * @param dataSource the data source to save.
    * @param stored     the data source stored at the path being saved, {@code null} if none.
    * @param principal  the user.
    */
   public void checkDataSource(XDataSource dataSource, XDataSource stored, Principal principal) {
      File refused = getRefusedPath(dataSource, stored, principal);

      if(refused != null) {
         Catalog catalog = Catalog.getCatalog(principal);
         String message = isEmptyPath(refused) ?
            catalog.getString("data.datasources.serverPathRequired") :
            catalog.getString("data.datasources.serverPathNotAllowed", refused.getPath());
         throw new MessageException(message, LogLevel.WARN, false);
      }
   }

   /**
    * Gets a server path of a data source that a user may not save.
    *
    * @param dataSource the data source to save.
    * @param stored     the data source stored at the path being saved, {@code null} if none.
    * @param principal  the user.
    *
    * @return the first path that is not allowed, {@code null} if all are allowed. A required
    *         path that is not set is returned as an empty path (see {@link #isEmptyPath(File)}).
    */
   public File getRefusedPath(XDataSource dataSource, XDataSource stored, Principal principal) {
      if(!(dataSource instanceof TabularDataSource<?>)) {
         return null;
      }

      Map<String, File> paths = getServerPaths(dataSource);

      if(paths.isEmpty() || isUnrestricted(principal)) {
         return null;
      }

      boolean sameType = stored != null && stored.getClass() == dataSource.getClass();
      Map<String, File> storedPaths = sameType ? getServerPaths(stored) : Collections.emptyMap();
      Set<String> requiredPaths = getRequiredServerPaths(dataSource.getClass());
      List<Path> roots = null;

      for(Map.Entry<String, File> entry : paths.entrySet()) {
         File file = entry.getValue();

         // a required path that is not set, such as the root folder of a Text/Excel Directory
         // data source, would leave the data source without a folder to keep its queries in, so
         // it is only allowed when the stored data source doesn't have one either
         if(isEmptyPath(file)) {
            if(requiredPaths.contains(entry.getKey()) &&
               !(sameType && isEmptyPath(storedPaths.get(entry.getKey()))))
            {
               return new File("");
            }

            continue;
         }

         if(isSamePath(file, storedPaths.get(entry.getKey()))) {
            continue;
         }

         if(roots == null) {
            roots = getAllowedRoots(principal);
         }

         if(!isUnderAnyRoot(file, roots)) {
            return file;
         }
      }

      return null;
   }

   /**
    * Gets the server paths that a tabular bean stores, that is the values of its
    * {@link Property} annotated properties of type {@link File}, which the FILE editor sets.
    *
    * @return the paths by property name, a value may be {@code null}.
    */
   public static Map<String, File> getServerPaths(Object bean) {
      Map<String, File> paths = new LinkedHashMap<>();

      if(bean == null) {
         return paths;
      }

      for(PropertyMeta prop : TabularUtil.findProperties(bean.getClass())) {
         if(isServerPath(prop)) {
            paths.put(prop.getName(), (File) prop.getValue(bean));
         }
      }

      return paths;
   }

   /**
    * Determines if a tabular bean class has a server path, a {@link Property} annotated property
    * of type {@link File}.
    */
   public static boolean hasServerPaths(Class<?> cls) {
      if(cls == null) {
         return false;
      }

      for(PropertyMeta prop : TabularUtil.findProperties(cls)) {
         if(isServerPath(prop)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines if a server path is not set, that is {@code null} or an empty path.
    */
   public static boolean isEmptyPath(File file) {
      return file == null || file.getPath().isBlank();
   }

   private static Set<String> getRequiredServerPaths(Class<?> cls) {
      Set<String> names = new HashSet<>();

      for(PropertyMeta prop : TabularUtil.findProperties(cls)) {
         if(isServerPath(prop) && prop.getProperty() != null && prop.getProperty().required()) {
            names.add(prop.getName());
         }
      }

      return names;
   }

   private static boolean isServerPath(PropertyMeta prop) {
      Class<?> type = prop.getDescriptor().getPropertyType();
      return type != null && File.class.isAssignableFrom(type) &&
         prop.getDescriptor().getReadMethod() != null;
   }

   /**
    * Determines if a path is one of the roots or under one.
    */
   public static boolean isUnderAnyRoot(File file, List<Path> roots) {
      if(file == null || roots == null) {
         return false;
      }

      for(Path root : roots) {
         if(isContained(root, file.toPath())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Determines if a path is a directory or under it. Symbolic links are resolved on both paths,
    * so a link under the directory to a place outside of it is not contained. A path that does
    * not exist is resolved through its deepest existing ancestor. Paths on different file system
    * roots, such as different drives, are not contained.
    *
    * @param root      the directory.
    * @param candidate the path to check.
    */
   public static boolean isContained(Path root, Path candidate) {
      if(root == null || candidate == null) {
         return false;
      }

      try {
         Path realRoot = toRealPath(root);
         Path realCandidate = toRealPath(candidate);

         // Path.startsWith compares whole name elements, so /data2 is not under /data
         return realRoot != null && realCandidate != null && realCandidate.startsWith(realRoot);
      }
      catch(Exception e) {
         LOG.debug("Failed to check if {} is under {}", candidate, root, e);
         return false;
      }
   }

   /**
    * Returns a file only when it is the folder or under it, as {@link #isUnderFolder(Path, Path)}
    * checks. A folder of <code>/</code> stands for every drive of the server and contains any
    * file. Callers use the returned value, never the file they passed in, so a path such as
    * <code>../..</code> that leaves the folder is dropped here.
    *
    * @param folder the folder, such as the root folder of a data source.
    * @param file   the file to check.
    *
    * @return the file, or <code>null</code> if it isn't under the folder or either is missing.
    */
   public static File getFileUnderFolder(String folder, File file) {
      if(folder == null || file == null) {
         return null;
      }

      if("/".equals(folder)) {
         return file;
      }

      try {
         return isUnderFolder(Paths.get(folder), file.toPath()) ? file : null;
      }
      catch(InvalidPathException e) {
         LOG.debug("Failed to check if {} is under {}", file, folder, e);
         return null;
      }
   }

   /**
    * Determines if a path is a folder or under it once both are made absolute and their parent
    * (..) elements are removed. Unlike {@link #isContained(Path, Path)} symbolic links are not
    * resolved, so a link that was placed in the folder on the server keeps working. It is used
    * to keep the paths of a query, which a user types or picks, in the root folder of its data
    * source.
    *
    * @param folder    the folder.
    * @param candidate the path to check.
    */
   public static boolean isUnderFolder(Path folder, Path candidate) {
      if(folder == null || candidate == null) {
         return false;
      }

      try {
         return candidate.toAbsolutePath().normalize()
            .startsWith(folder.toAbsolutePath().normalize());
      }
      catch(Exception e) {
         LOG.debug("Failed to check if {} is under {}", candidate, folder, e);
         return false;
      }
   }

   /**
    * Determines if two paths are the same after they are made absolute and normalized.
    */
   public static boolean isSamePath(File file1, File file2) {
      if(file1 == null || file2 == null) {
         return false;
      }

      try {
         return file1.toPath().toAbsolutePath().normalize()
            .equals(file2.toPath().toAbsolutePath().normalize());
      }
      catch(InvalidPathException e) {
         return false;
      }
   }

   /**
    * Parses the value of the allowed roots property. Empty and relative entries are ignored.
    */
   public static List<Path> parseRoots(String value) {
      List<Path> roots = new ArrayList<>();

      if(value == null || value.isBlank()) {
         return roots;
      }

      for(String item : value.split("[,;]")) {
         String path = item.trim();

         if(path.isEmpty()) {
            continue;
         }

         try {
            Path root = Paths.get(path);

            if(!root.isAbsolute()) {
               LOG.warn("Ignoring the relative path {} in the {} property, only absolute " +
                           "directories are allowed", path, ALLOWED_ROOTS_PROPERTY);
               continue;
            }

            roots.add(root.normalize());
         }
         catch(InvalidPathException e) {
            LOG.warn("Ignoring the invalid path {} in the {} property", path,
                     ALLOWED_ROOTS_PROPERTY);
         }
      }

      return roots;
   }

   /**
    * Resolves a path to its real path. A path that does not exist is resolved through its
    * deepest existing ancestor, unless the rest of it contains a parent (..) element, which can
    * only be resolved correctly once it exists.
    *
    * @return the real path, or {@code null} if it can't be resolved.
    */
   private static Path toRealPath(Path path) throws IOException {
      Path absolute = path.toAbsolutePath();

      if(Files.exists(absolute)) {
         return absolute.toRealPath();
      }

      Path ancestor = absolute.getParent();

      while(ancestor != null && !Files.exists(ancestor)) {
         ancestor = ancestor.getParent();
      }

      if(ancestor == null) {
         return absolute.normalize();
      }

      Path rest = ancestor.relativize(absolute);

      for(Path name : rest) {
         if("..".equals(name.toString())) {
            return null;
         }
      }

      return ancestor.toRealPath().resolve(rest).normalize();
   }

   /**
    * Reads the allowed roots property for the user's organization, falling back to the global
    * value.
    */
   private static String getAllowedRootsProperty(Principal principal) {
      if(principal != null) {
         try {
            String orgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

            if(orgID != null) {
               String value = SreeEnv.getProperty(
                  "inetsoft.org." + orgID + "." + ALLOWED_ROOTS_PROPERTY, false, false);

               if(value != null) {
                  return value;
               }
            }
         }
         catch(Exception e) {
            LOG.debug("Failed to get the organization of {}", principal, e);
         }
      }

      return SreeEnv.getProperty(ALLOWED_ROOTS_PROPERTY, false, false);
   }

   private final BooleanSupplier securityEnabled;
   private final Predicate<Principal> siteAdmin;
   private final Function<Principal, String> allowedRoots;
   private static final Logger LOG = LoggerFactory.getLogger(ServerFilePathPolicy.class);
}
