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
package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.BrowsableQuery;
import inetsoft.uql.tabular.ColumnDefinition;
import inetsoft.uql.tabular.SelectableTabularQuery;

import java.util.List;

/**
 * Minimal, REAL (non-mock) stand-in for a {@link BrowsableQuery} such as {@code OneDriveQuery} --
 * exists only so {@code WizTabularController.browse()}'s {@code instanceof BrowsableQuery} test
 * has something to answer {@code true} for, without depending on the {@code inetsoft-onedrive}
 * connector module (core does not depend on it). Mirrors {@link FakeSelectableFileQuery}'s
 * existing pattern.
 */
public class FakeBrowsableQuery extends SelectableTabularQuery implements BrowsableQuery {
   public FakeBrowsableQuery() {
      super("FakeBrowsable");
   }

   @Override
   protected ColumnDefinition[] loadColumns() {
      return new ColumnDefinition[0];
   }

   @Override
   public String getBrowsablePropertyName() {
      return "path";
   }

   @Override
   public List<String> getAcceptedExtensions() {
      return acceptedExtensions;
   }

   public void setAcceptedExtensions(List<String> acceptedExtensions) {
      this.acceptedExtensions = acceptedExtensions;
   }

   @Override
   public BrowseListing browseChildren(String path, boolean recursive, List<String> acceptTypes,
                                        int maxEntries)
      throws Exception
   {
      lastPath = path;
      lastRecursive = recursive;
      lastAcceptTypes = acceptTypes;
      lastMaxEntries = maxEntries;

      if(failure != null) {
         throw failure;
      }

      return canned;
   }

   public void setCanned(BrowseListing canned) {
      this.canned = canned;
   }

   public void setFailure(Exception failure) {
      this.failure = failure;
   }

   public String getLastPath() {
      return lastPath;
   }

   public boolean isLastRecursive() {
      return lastRecursive;
   }

   public List<String> getLastAcceptTypes() {
      return lastAcceptTypes;
   }

   public int getLastMaxEntries() {
      return lastMaxEntries;
   }

   private List<String> acceptedExtensions = List.of(".csv", ".txt");
   private BrowseListing canned = new BrowseListing(List.of(), false);
   private Exception failure;
   private String lastPath;
   private boolean lastRecursive;
   private List<String> lastAcceptTypes;
   private int lastMaxEntries;
}
