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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, REAL (non-mock) stand-in for a generic/custom REST-JSON query such as
 * {@code RestJsonQuery} -- has a directly-settable {@code suffix} (unlike
 * {@link FakeNamedConnectorQuery}'s no-op override) and NO {@code endpoint} property.
 *
 * <p>Only two custom lookup levels are implemented (0 and 1) -- enough to exercise a two-level
 * chain and the per-level ordering dependency ({@code lookupUrl}<i>i</i> must be set before
 * {@code lookupJsonPath}<i>i</i>/{@code lookupKey}<i>i</i>/{@code lookupIgnoreBaseUrl}<i>i</i>,
 * since it is what grows the backing lists to include index <i>i</i> at all, mirroring
 * {@code RestJsonQuery.setLookupURL}/{@code addLookupQuery}) -- declared via
 * {@code @PropertyEditor(dependsOn = "lookupUrl"+i)} on the other three, matching the real
 * {@code RestJsonQuery}'s own dependsOn addition. The "chain longer than 5" guard is checked by
 * {@code TabularQueryContractSupport}'s custom-lookup-URL-placeholder validation BEFORE any
 * property write (by name pattern against the top-level queryParams keys), so it needs no
 * backing level here to test.</p>
 *
 * <p>{@code @View} is REQUIRED -- see {@link FakeNamedConnectorQuery}'s own doc for why.</p>
 */
@View(vertical = true, value = {
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("lookupUrl0"),
   @View1("lookupJsonPath0"),
   @View1("lookupKey0"),
   @View1("lookupIgnoreBaseUrl0"),
   @View1("lookupUrl1"),
   @View1("lookupJsonPath1"),
   @View1("lookupKey1"),
   @View1("lookupIgnoreBaseUrl1"),
})
public class FakeCustomRestQuery extends TabularQuery {
   public FakeCustomRestQuery() {
      super("FakeCustomRest");
   }

   @Property(label = "Suffix")
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Property(label = "Json Path")
   public String getJsonPath() {
      return jsonPath;
   }

   public void setJsonPath(String jsonPath) {
      this.jsonPath = jsonPath;
   }

   @Property(label = "Lookup Url 0")
   public String getLookupUrl0() {
      return getLookupURL(0);
   }

   public void setLookupUrl0(String v) {
      setLookupURL(0, v);
   }

   @Property(label = "Lookup Json Path 0")
   @PropertyEditor(dependsOn = "lookupUrl0")
   public String getLookupJsonPath0() {
      return getLookupJsonPath(0);
   }

   public void setLookupJsonPath0(String v) {
      setLookupJsonPath(0, v);
   }

   @Property(label = "Lookup Key 0")
   @PropertyEditor(dependsOn = "lookupUrl0")
   public String getLookupKey0() {
      return getLookupKey(0);
   }

   public void setLookupKey0(String v) {
      setLookupKey(0, v);
   }

   @Property(label = "Lookup Ignore Base Url 0")
   @PropertyEditor(dependsOn = "lookupUrl0")
   public boolean getLookupIgnoreBaseUrl0() {
      return getLookupIgnoreBaseUrl(0);
   }

   public void setLookupIgnoreBaseUrl0(boolean v) {
      setLookupIgnoreBaseUrl(0, v);
   }

   @Property(label = "Lookup Url 1")
   public String getLookupUrl1() {
      return getLookupURL(1);
   }

   public void setLookupUrl1(String v) {
      setLookupURL(1, v);
   }

   @Property(label = "Lookup Json Path 1")
   @PropertyEditor(dependsOn = "lookupUrl1")
   public String getLookupJsonPath1() {
      return getLookupJsonPath(1);
   }

   public void setLookupJsonPath1(String v) {
      setLookupJsonPath(1, v);
   }

   @Property(label = "Lookup Key 1")
   @PropertyEditor(dependsOn = "lookupUrl1")
   public String getLookupKey1() {
      return getLookupKey(1);
   }

   public void setLookupKey1(String v) {
      setLookupKey(1, v);
   }

   @Property(label = "Lookup Ignore Base Url 1")
   @PropertyEditor(dependsOn = "lookupUrl1")
   public boolean getLookupIgnoreBaseUrl1() {
      return getLookupIgnoreBaseUrl(1);
   }

   public void setLookupIgnoreBaseUrl1(boolean v) {
      setLookupIgnoreBaseUrl(1, v);
   }

   // Non-@Property generic accessors, mirroring RestJsonQuery's own shape and its exact
   // silent-no-op-past-the-limit behavior for each backing list.

   public String getLookupURL(int i) {
      return i < lookupUrls.size() ? lookupUrls.get(i) : null;
   }

   public void setLookupURL(int i, String url) {
      if(url == null) {
         return;
      }

      if(i >= lookupUrls.size()) {
         if(i >= LIMIT) {
            return;
         }

         while(lookupUrls.size() <= i) {
            addLevel();
         }
      }

      lookupUrls.set(i, url);
   }

   public String getLookupJsonPath(int i) {
      return i < lookupJsonPaths.size() ? lookupJsonPaths.get(i) : null;
   }

   public void setLookupJsonPath(int i, String jsonPath) {
      if(i < lookupJsonPaths.size()) {
         lookupJsonPaths.set(i, jsonPath);
      }
      // else: silently no-op, same as RestJsonQuery -- this level's url must be set first.
   }

   public String getLookupKey(int i) {
      return i < lookupKeys.size() ? lookupKeys.get(i) : null;
   }

   public void setLookupKey(int i, String key) {
      if(i < lookupKeys.size()) {
         lookupKeys.set(i, key);
      }
   }

   public boolean getLookupIgnoreBaseUrl(int i) {
      return i < lookupIgnoreBaseUrl.size() && lookupIgnoreBaseUrl.get(i);
   }

   public void setLookupIgnoreBaseUrl(int i, boolean ignoreBaseUrl) {
      if(i < lookupIgnoreBaseUrl.size()) {
         lookupIgnoreBaseUrl.set(i, ignoreBaseUrl);
      }
   }

   /** Mirrors {@code RestJsonQuery.addLookupQuery} -- grows all four backing lists together. */
   private void addLevel() {
      lookupUrls.add("{param" + (lookupUrls.size() + 1) + "}");
      lookupJsonPaths.add("");
      lookupKeys.add("");
      lookupIgnoreBaseUrl.add(false);
   }

   private static final int LIMIT = 2;
   private String suffix;
   private String jsonPath;
   private final List<String> lookupUrls = new ArrayList<>();
   private final List<String> lookupJsonPaths = new ArrayList<>();
   private final List<String> lookupKeys = new ArrayList<>();
   private final List<Boolean> lookupIgnoreBaseUrl = new ArrayList<>();
}
