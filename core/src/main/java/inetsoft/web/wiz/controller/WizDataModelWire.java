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

import java.util.List;

/**
 * The kind-agnostic request and response records of the data model bridge
 * ({@code POST /api/wiz/data-models/<route>}), frozen in lane J1.0.
 *
 * <p>Field names are the wire contract with the plugin's {@code dataModel/common/bridgeTypes.ts};
 * {@code WizDataModelBridgeWireTest} pins them. A change here is a contract change and goes
 * through the lead. Dates are epoch milliseconds or null. The per-kind definition shapes live in
 * {@code WizLogicalModelWire}, {@code WizVpmWire} and {@code WizPhysicalViewWire}.</p>
 */
public final class WizDataModelWire {
   private WizDataModelWire() {
   }

   /** {@code kind} values of a {@link Ref}. */
   public static final String KIND_PHYSICAL_VIEW = "physicalView";
   public static final String KIND_LOGICAL_MODEL = "logicalModel";
   public static final String KIND_VPM = "vpm";
   public static final String KIND_FOLDER = "folder";

   /** The business-outcome {@code reason} vocabulary (every route). */
   public static final String NOT_FOUND = "NOT_FOUND";
   public static final String WRONG_KIND = "WRONG_KIND";
   public static final String NOT_JDBC = "NOT_JDBC";
   public static final String DUPLICATE_NAME = "DUPLICATE_NAME";
   public static final String INVALID = "INVALID";
   public static final String FOLDER_NOT_FOUND = "FOLDER_NOT_FOUND";
   public static final String HAS_DEPENDENCIES = "HAS_DEPENDENCIES";
   public static final String VIEW_IN_USE = "VIEW_IN_USE";
   public static final String HAS_EXTENDED_CHILDREN = "HAS_EXTENDED_CHILDREN";
   public static final String CONTAINED_NOT_DELETABLE = "CONTAINED_NOT_DELETABLE";
   public static final String EXPECT_MISMATCH = "EXPECT_MISMATCH";
   public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
   public static final String UNJOINED_TABLES_PROHIBITED = "UNJOINED_TABLES_PROHIBITED";
   public static final String STALE_VIEW = "STALE_VIEW";
   public static final String STALE_MODEL = "STALE_MODEL";
   public static final String EXTENDED_UNSUPPORTED = "EXTENDED_UNSUPPORTED";
   public static final String BASE_ELEMENT_READ_ONLY = "BASE_ELEMENT_READ_ONLY";
   public static final String READ_BACK_FAILED = "READ_BACK_FAILED";
   public static final String METADATA_UNAVAILABLE = "METADATA_UNAVAILABLE";
   public static final String UNKNOWN = "UNKNOWN";

   /** A reference to a model, view, VPM or folder; {@code parent} names the base of an extended one. */
   public record Ref(String kind, String name, String parent) {
   }

   /** One field-named validation problem. */
   public record Err(String field, String message, String code) {
   }

   /** One asset that depends on a model (type: worksheet, viewsheet, logicalModel, ...). */
   public record Dependent(String type, String name, String path, String element) {
   }

   /** The {@code ok:false} body of every business outcome. */
   public record Failure(boolean ok, String reason, String message, String field,
                         List<Err> errors, String existingKind)
   {
      public static Failure of(String reason, String message) {
         return new Failure(false, reason, message, null, null, null);
      }
   }

   // /browse
   public record BrowseRequest(String database, String folder, String query, List<String> kinds,
                               Integer limit)
   {
   }

   public record BrowseFolder(String name, boolean editable, boolean deletable, String createdBy,
                              Long createdDate)
   {
   }

   public record BrowseItem(String kind, String name, String folder, String description,
                            boolean extended, String parent, boolean editable, boolean deletable,
                            String createdBy, Long createdDate)
   {
   }

   public record BrowseResponse(String database, String folder, String query,
                                List<BrowseFolder> folders, List<BrowseItem> items, int total)
   {
   }

   // /physical/tables
   public record PhysicalTablesRequest(String database, String view, String connection) {
   }

   public record PhysicalColumn(String name, String dataType) {
   }

   public record PhysicalTable(String name, List<PhysicalColumn> columns) {
   }

   public record PhysicalTablesResponse(String database, String view,
                                        List<PhysicalTable> tables)
   {
   }

   // /dependencies
   public record DependenciesRequest(String database, List<Ref> items) {
   }

   public record AlsoDeleted(String kind, String name, String reason) {
   }

   public record Dangling(String kind, String name, String via) {
   }

   public record DepItem(String kind, String name, String folder, boolean exists,
                         boolean deletable, String reason, String message,
                         List<Dependent> dependents, String text, List<Ref> contains,
                         List<AlsoDeleted> alsoDeleted, List<Dangling> danglingAfter)
   {
   }

   public record DependenciesResponse(List<DepItem> items, String coverage) {
   }

   // /delete
   public record DeleteItem(String kind, String name, String parent, Boolean force,
                            List<Ref> expect)
   {
   }

   public record DeleteRequest(String database, List<DeleteItem> items) {
   }

   public record DeleteResult(String kind, String name, boolean ok, String reason, String message,
                              List<Dependent> dependents, List<Ref> deleted)
   {
   }

   public record DeleteResponse(List<DeleteResult> results) {
   }

   // /rename
   public record RenameRequest(String database, String kind, String name, String newName,
                               String description)
   {
   }

   public record RenameResponse(boolean ok, String name, String folder) {
   }

   // /move
   public record MoveRequest(String database, String folder, List<Ref> items) {
   }

   public record MoveResult(String kind, String name, boolean ok, String reason, String message,
                            String from, String to)
   {
   }

   public record MoveResponse(List<MoveResult> results) {
   }

   // /folders/create, /folders/rename
   public record FolderCreateRequest(String database, String name) {
   }

   public record FolderCreateResponse(boolean ok, String name) {
   }

   public record FolderRenameRequest(String database, String name, String newName) {
   }

   public record FolderRenameResponse(boolean ok, String name, List<Ref> moved) {
   }
}
