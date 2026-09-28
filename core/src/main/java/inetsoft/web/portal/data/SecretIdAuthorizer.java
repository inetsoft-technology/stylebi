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
package inetsoft.web.portal.data;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.TabularDataSource;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.credential.CloudCredential;
import inetsoft.util.credential.Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.*;
import java.util.function.Predicate;

/**
 * Decides which cloud secret ids a user may reference from a data source. Secret ids are not
 * scoped to a data source or an organization, so any id the server identity can read would
 * otherwise be resolved for whoever names it. An id may be used by a caller if one of these holds:
 * <ul>
 *    <li>it is already stored on the data source being saved;</li>
 *    <li>a saved data source in the current organization that the caller can write, or one of
 *    its additional connections, already stores it. The caller can already see that secret by
 *    editing that data source;</li>
 *    <li>the caller is a site administrator, or an organization administrator when
 *    multi-tenancy is off.</li>
 * </ul>
 */
public class SecretIdAuthorizer {
   public SecretIdAuthorizer(SecurityEngine securityEngine, DataSourceRegistry dataSourceRegistry) {
      this.securityEngine = securityEngine;
      this.dataSourceRegistry = dataSourceRegistry;
   }

   /**
    * Creates a check for the secret ids referenced by a data source that is being saved or
    * tested. The results are cached, so the check should only be used for one request.
    *
    * @param stored    the data source that is stored at the path being saved, or {@code null} if
    *                  there is none. The ids it and its additional connections store are allowed.
    * @param principal the caller.
    *
    * @return the check.
    */
   public Predicate<String> createCheck(XDataSource stored, Principal principal) {
      Set<String> storedIds = getCloudSecretIds(stored);
      Map<String, Boolean> authorized = new HashMap<>();
      return secretId -> Tool.isEmptyString(secretId) || authorized.computeIfAbsent(
         secretId, id -> storedIds.contains(id) || isAuthorized(id, principal));
   }

   /**
    * Throws an exception if a secret id may not be used.
    *
    * @param secretId the secret id, may be {@code null}.
    * @param check    the check created by {@link #createCheck(XDataSource, Principal)}.
    */
   public static void checkSecretId(String secretId, Predicate<String> check) {
      if(!Tool.isEmptyString(secretId) && !check.test(secretId)) {
         throw new MessageException(
            Catalog.getCatalog().getString("data.datasources.secretIdNotAllowed"));
      }
   }

   /**
    * Determines if a caller may use a secret id that is not stored on the data source being
    * saved.
    */
   public boolean isAuthorized(String secretId, Principal principal) {
      return canIntroduceSecretIds(principal) ||
         isStoredOnWritableDataSource(secretId, null, principal);
   }

   /**
    * Determines if a saved data source in the current organization that the caller can write, or
    * one of its additional connections, stores a secret id.
    *
    * @param secretId  the secret id.
    * @param firstPath the path of a data source to check first, may be {@code null}.
    * @param principal the caller.
    */
   public boolean isStoredOnWritableDataSource(String secretId, String firstPath,
                                               Principal principal)
   {
      if(principal == null || Tool.isEmptyString(secretId)) {
         return false;
      }

      try {
         if(firstPath != null && isSecretIdStoredOn(firstPath, secretId, principal)) {
            return true;
         }

         for(String path : dataSourceRegistry.getDataSourceFullNames()) {
            if(!path.equals(firstPath) && isSecretIdStoredOn(path, secretId, principal)) {
               return true;
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to check access to a secret id referenced by a data source", e);
      }

      return false;
   }

   /**
    * Determines if a caller may reference secret ids that no data source uses yet.
    */
   private boolean canIntroduceSecretIds(Principal principal) {
      if(principal == null) {
         return false;
      }

      try {
         if(!securityEngine.isSecurityEnabled()) {
            return true;
         }

         if(!(principal instanceof XPrincipal)) {
            return false;
         }

         OrganizationManager organizationManager = OrganizationManager.getInstance();
         return organizationManager.isSiteAdmin(principal) ||
            organizationManager.isOrgAdmin(principal) && !SUtil.isMultiTenant();
      }
      catch(Exception e) {
         LOG.warn("Failed to check if a user may reference new secret ids", e);
         return false;
      }
   }

   private boolean isSecretIdStoredOn(String path, String secretId, Principal principal)
      throws SecurityException
   {
      if(!securityEngine.checkPermission(
         principal, ResourceType.DATA_SOURCE, path, ResourceAction.WRITE))
      {
         return false;
      }

      return getCloudSecretIds(dataSourceRegistry.getDataSource(path)).contains(secretId);
   }

   /**
    * Gets the cloud secret ids that a data source and its additional connections store.
    */
   public static Set<String> getCloudSecretIds(XDataSource dataSource) {
      Set<String> ids = new HashSet<>();
      addCloudSecretId(ids, dataSource);

      if(dataSource instanceof AdditionalConnectionDataSource<?> parent &&
         parent.getDataSourceNames() != null)
      {
         for(String name : parent.getDataSourceNames()) {
            addCloudSecretId(ids, parent.getDataSource(name));
         }
      }

      return ids;
   }

   private static void addCloudSecretId(Set<String> ids, XDataSource dataSource) {
      String id = getCloudSecretId(dataSource);

      if(!Tool.isEmptyString(id)) {
         ids.add(id);
      }
   }

   /**
    * Gets the cloud secret id that a data source references.
    *
    * @return the id, or {@code null} if the data source does not use a cloud secret.
    */
   public static String getCloudSecretId(XDataSource dataSource) {
      Credential credential = null;

      if(dataSource instanceof TabularDataSource<?> tabular) {
         credential = tabular.getCredential();
      }
      else if(dataSource instanceof JDBCDataSource jdbc) {
         credential = jdbc.getCredential();
      }
      else if(dataSource instanceof XMLADataSource xmla) {
         credential = xmla.getCredential();
      }

      return credential instanceof CloudCredential ? credential.getId() : null;
   }

   private final SecurityEngine securityEngine;
   private final DataSourceRegistry dataSourceRegistry;
   private static final Logger LOG = LoggerFactory.getLogger(SecretIdAuthorizer.class);
}
