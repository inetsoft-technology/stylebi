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
package inetsoft.analytic.composition.event;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Check if MV is pending for the viewsheet.
 *
 * @author InetSoft Technology Corp
 * @version 11.1
 */
public class CheckMissingMVEvent implements AssetObject {
   /**
    * Constructor.
    */
   public CheckMissingMVEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public CheckMissingMVEvent(AssetEntry entry) {
      this.entry = entry;
   }

   public AssetEntry getEntry() {
      return entry;
   }

   public void setEntry(AssetEntry entry) {
      this.entry = entry;
   }

   public boolean isRefreshDirectly() {
      return refreshDirectly;
   }

   public void setRefreshDirectly(boolean refreshDirectly) {
      this.refreshDirectly = refreshDirectly;
   }

   public boolean isBackground() {
      return background;
   }

   public void setBackground(boolean background) {
      this.background = background;
   }

   @Override
   public Object clone() {
      try {
         CheckMissingMVEvent event = (CheckMissingMVEvent) super.clone();
         event.entry = (AssetEntry) entry.clone();
         return event;
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to clone CheckMissingMVEvent", ex);
      }

      return null;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      // ignore
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      // ignore
   }

   private AssetEntry entry;
   private boolean refreshDirectly;
   private boolean background;

   private static final Logger LOG = LoggerFactory.getLogger(CheckMissingMVEvent.class);
}
