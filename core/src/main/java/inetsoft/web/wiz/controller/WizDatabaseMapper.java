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


package inetsoft.web.wiz.controller;

import inetsoft.report.internal.Util;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.*;
import inetsoft.web.admin.content.database.types.AccessDatabaseType.AccessDatabaseInfo;
import inetsoft.web.wiz.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.Map;

/**
 * Converts between the wiz wire form of a JDBC database ({@link WizDatabaseDefinition}) and
 * StyleBI's own {@link DatabaseDefinition}.
 *
 * <p>Extracted from {@code WizDatabaseController} so the additional-connection endpoints convert
 * exactly as the main database endpoints do; the rules for {@code oldName}, the password and the
 * credential reference are documented there and on {@link WizAuthentication}.</p>
 */
class WizDatabaseMapper {
   WizDatabaseMapper(DatabaseTypeService databaseTypeService) {
      this.databaseTypeService = databaseTypeService;
   }

   /**
    * Keeps the stored cloud-secret reference when the caller expressed no authentication choice.
    *
    * <p>The caller expresses a choice by setting {@code useCredentialId} or a {@code credentialId},
    * or by supplying a password (anything other than the "unchanged" placeholder). Only when none
    * of the three is present is the stored reference carried forward, so a connection edit that
    * does not mention authentication cannot silently drop the credential reference (the original
    * reason this carry-over exists), while a value the caller did set is never overwritten.</p>
    */
   static void carryOverCredentialReference(DatabaseDefinition current, DatabaseDefinition updated) {
      AuthenticationDetails currentAuth = current == null ? null : current.getAuthentication();
      AuthenticationDetails updatedAuth = updated == null ? null : updated.getAuthentication();

      if(currentAuth == null || updatedAuth == null) {
         return;
      }

      boolean callerChoseCredential = updatedAuth.isUseCredentialId() ||
         updatedAuth.getCredentialId() != null;
      boolean callerChosePassword = !Util.PLACEHOLDER_PASSWORD.equals(updatedAuth.getPassword());

      if(!callerChoseCredential && !callerChosePassword) {
         updatedAuth.setUseCredentialId(currentAuth.isUseCredentialId());
         updatedAuth.setCredentialId(currentAuth.getCredentialId());
      }
   }

   /**
    * Carries the unedited settings from the stored definition onto the rebuilt one: {@code unasgn},
    * the credential reference and the connection-info settings the wiz contract does not
    * round-trip. Shared by the base-source and additional-connection update paths.
    */
   static void carryOverUneditedSettings(DatabaseDefinition current, DatabaseDefinition updated) {
      if(current == null || updated == null) {
         return;
      }

      updated.setUnasgn(current.isUnasgn());
      carryOverCredentialReference(current, updated);
      carryOverUneditedInfo(current, updated);
   }

   /**
    * Keeps the URL properties (and the Access data source name) of the stored info when the
    * database type is unchanged. The wiz contract does not expose them, so the rebuilt info has
    * them null and the formatted URL would silently lose them. Nothing is carried on a type
    * switch, since properties are driver specific.
    */
   static void carryOverUneditedInfo(DatabaseDefinition current, DatabaseDefinition updated) {
      DatabaseInfo currentInfo = current == null ? null : current.getInfo();
      DatabaseInfo updatedInfo = updated == null ? null : updated.getInfo();
      String currentType = current == null ? null : current.getType();
      String updatedType = updated == null ? null : updated.getType();

      if(currentInfo == null || updatedInfo == null || currentType == null ||
         updatedType == null || !currentType.equalsIgnoreCase(updatedType))
      {
         return;
      }

      if(updatedInfo.getProperties() == null) {
         updatedInfo.setProperties(currentInfo.getProperties());
      }

      if(currentInfo instanceof AccessDatabaseInfo && updatedInfo instanceof AccessDatabaseInfo) {
         AccessDatabaseInfo updatedAccess = (AccessDatabaseInfo) updatedInfo;

         if(updatedAccess.getDataSourceName() == null) {
            updatedAccess.setDataSourceName(((AccessDatabaseInfo) currentInfo).getDataSourceName());
         }
      }
   }

   WizDatabaseDefinition toWizDefinition(DatabaseDefinition definition) {
      if(definition == null) {
         return null;
      }

      NetworkLocation network = definition.getNetwork();
      WizNetworkLocation wizNetwork = network == null
         ? null : new WizNetworkLocation(network.getHostName(), network.getPortNumber());
      AuthenticationDetails authentication = definition.getAuthentication();

      // The password is dropped, never masked. StyleBI's own model sends a placeholder constant
      // here, but a client that returned that constant unchanged would be indistinguishable from a
      // user who had typed it, and the wiz contract expresses "unchanged" as null instead.
      // The credential id is a reference to a cloud secret, never the secret itself, so it is safe to
      // return; it is what lets a client see that a connection authenticates through one.
      WizAuthentication wizAuthentication = authentication == null
         ? new WizAuthentication(false, null, null)
         : new WizAuthentication(authentication.isRequired(), authentication.getUserName(), null,
                                 authentication.isUseCredentialId(),
                                 authentication.getCredentialId());

      return new WizDatabaseDefinition(
         definition.getName(), definition.getDescription(), definition.getType(), wizNetwork,
         wizAuthentication, toWizInfo(definition.getInfo()), definition.isAnsiJoin(),
         definition.getTransactionIsolation(), definition.getTableNameOption(),
         definition.getDefaultDatabase(), definition.isChangeDefaultDB(),
         definition.isDeletable());
   }

   private WizDatabaseInfo toWizInfo(DatabaseInfo info) {
      if(info == null) {
         return null;
      }

      String databaseName = null;
      String sid = null;
      String instanceName = null;
      String serverName = null;
      String databaseLocale = null;
      String driverClass = null;
      String jdbcUrl = null;
      String testQuery = null;

      // Dispatched on the subclass rather than on the type identifier: the two agree, but only the
      // subclass can be trusted to actually have the accessor being called.
      if(info instanceof DatabaseNameInfo nameInfo) {
         databaseName = nameInfo.getDatabaseName();
      }

      if(info instanceof OracleDatabaseType.OracleDatabaseInfo oracleInfo) {
         sid = oracleInfo.getSid();
      }

      if(info instanceof SQLServerDatabaseType.SQLServerDatabaseInfo sqlServerInfo) {
         instanceName = sqlServerInfo.getInstanceName();
      }

      if(info instanceof InformixDatabaseType.InformixDatabaseInfo informixInfo) {
         serverName = informixInfo.getServerName();
         databaseLocale = informixInfo.getDatabaseLocale();
      }

      if(info instanceof CustomDatabaseType.CustomDatabaseInfo customInfo) {
         driverClass = customInfo.getDriverClass();
         jdbcUrl = customInfo.getJdbcUrl();
         testQuery = customInfo.getTestQuery();
      }

      Map<String, String> poolProperties = info.getPoolProperties() == null
         ? null : new LinkedHashMap<>(info.getPoolProperties());

      return new WizDatabaseInfo(databaseName, sid, instanceName, serverName, databaseLocale,
                                 driverClass, jdbcUrl, testQuery, info.isCustomEditMode(),
                                 info.getCustomUrl(), poolProperties);
   }

   /**
    * Builds the definition the save and test paths take.
    *
    * @param definition the client's settings.
    * @param oldName    the name the database is stored under, or null when it does not exist yet.
    *                   Never taken from the client: it is the key to the stored password, and a save
    *                   without it leaves the password at the placeholder, skips {@code setPassword}
    *                   and then has the whole credential — placeholder and all — copied over the
    *                   real one. The password is gone with no error anywhere.
    */
   DatabaseDefinition toDatabaseDefinition(WizDatabaseDefinition definition,
                                                   String oldName)
   {
      DatabaseDefinition result = new DatabaseDefinition();
      result.setName(definition.name());
      result.setOldName(oldName);
      result.setType(definition.type());
      result.setDescription(definition.description());
      result.setInfo(toDatabaseInfo(definition.type(), definition.info()));

      // Always non-null: the URL formatters read the host and port unconditionally for every type
      // that has them, so a client that omitted the network would get a NullPointerException.
      NetworkLocation network = new NetworkLocation();

      if(definition.network() != null) {
         network.setHostName(definition.network().hostName());
         network.setPortNumber(definition.network().portNumber());
      }

      result.setNetwork(network);

      AuthenticationDetails authentication = new AuthenticationDetails();
      WizAuthentication wizAuthentication = definition.authentication();
      authentication.setRequired(wizAuthentication != null && wizAuthentication.required());

      if(wizAuthentication != null) {
         authentication.setUserName(wizAuthentication.userName());
         authentication.setUseCredentialId(wizAuthentication.useCredentialId());
         authentication.setCredentialId(wizAuthentication.credentialId());
      }

      // null means "leave the stored password alone", which the save path expresses as the
      // placeholder constant; paired with oldName above it recovers the real password. An empty
      // string is a deliberate clear and is passed through as itself.
      String password = wizAuthentication == null ? null : wizAuthentication.password();
      authentication.setPassword(password == null ? Util.PLACEHOLDER_PASSWORD : password);
      result.setAuthentication(authentication);

      result.setAnsiJoin(definition.ansiJoin());
      result.setTransactionIsolation(definition.transactionIsolation());
      result.setTableNameOption(definition.tableNameOption());
      result.setDefaultDatabase(definition.defaultDatabase());
      result.setChangeDefaultDB(definition.changeDefaultDB());
      result.setDeletable(definition.deletable());

      return result;
   }

   private DatabaseInfo toDatabaseInfo(String type, WizDatabaseInfo wizInfo) {
      DatabaseType<?> databaseType = databaseTypeService.getDatabaseType(type);

      if(databaseType == null) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown database type: " + type);
      }

      DatabaseInfo info = databaseType.createDatabaseInfo();

      // Never left null: the save path reads the pool properties unconditionally.
      info.setPoolProperties(wizInfo == null || wizInfo.poolProperties() == null
                                ? new TreeMap<>() : new TreeMap<>(wizInfo.poolProperties()));

      if(wizInfo != null) {
         info.setCustomEditMode(wizInfo.customEditMode());
         info.setCustomUrl(wizInfo.customUrl());

         if(info instanceof DatabaseNameInfo nameInfo) {
            nameInfo.setDatabaseName(wizInfo.databaseName());
         }

         if(info instanceof OracleDatabaseType.OracleDatabaseInfo oracleInfo) {
            oracleInfo.setSid(wizInfo.sid());
         }

         if(info instanceof SQLServerDatabaseType.SQLServerDatabaseInfo sqlServerInfo) {
            sqlServerInfo.setInstanceName(wizInfo.instanceName());
         }

         if(info instanceof InformixDatabaseType.InformixDatabaseInfo informixInfo) {
            informixInfo.setServerName(wizInfo.serverName());
            informixInfo.setDatabaseLocale(wizInfo.databaseLocale());
         }

         if(info instanceof CustomDatabaseType.CustomDatabaseInfo customInfo) {
            customInfo.setDriverClass(wizInfo.driverClass());
            customInfo.setJdbcUrl(wizInfo.jdbcUrl());
            customInfo.setTestQuery(wizInfo.testQuery());

            // A CUSTOM connection is always read back with customEditMode set, and in that mode the
            // stored URL is taken from customUrl, not from jdbcUrl. Pinning the flag and falling
            // back to jdbcUrl keeps a client that filled in only one of the two from saving a
            // database with no URL at all.
            customInfo.setCustomEditMode(true);

            if(customInfo.getCustomUrl() == null) {
               customInfo.setCustomUrl(wizInfo.jdbcUrl());
            }

            if(customInfo.getCustomUrl() == null || customInfo.getCustomUrl().isBlank()) {
               throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "info.jdbcUrl is required for a CUSTOM database");
            }
         }
         else if(info.isCustomEditMode() &&
                 (info.getCustomUrl() == null || info.getCustomUrl().isBlank()))
         {
            // The blank template is a CUSTOM definition with customEditMode set, so a client that
            // overlays a standard type on it carries that flag along. In custom-edit mode the stored
            // URL is the custom URL, which is empty here, so the source would be saved with no URL
            // at all and could then never be read, tested or updated. With no URL to honour, the
            // intent is the form fields: build the URL from host, port and database name.
            info.setCustomEditMode(false);
            info.setCustomUrl(null);
         }
      }

      return info;
   }

   private final DatabaseTypeService databaseTypeService;
}
