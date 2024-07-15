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
package inetsoft.mv;

import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.CancelledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVCreator, responsible for mv creation
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractMVCreator implements MVCreator {
   /**
    * Creates a new instance of <tt>AbstractMVCreator</tt>.
    *
    * @param def the view definition.
    */
   protected AbstractMVCreator(MVDef def)  {
      this.def = def;
   }

   /**
    * Create the MV.
    */
   protected abstract boolean create0() throws Exception;

   /**
    * Create mv.
    * @return true if created.
    */
   @Override
   public final boolean create() throws Exception {
      if(def.getWorksheet() == null) {
         LOG.error(
            "MV worksheet missing, ignored: {}. Check if sree.home property is correct",
            def.getName());
         return false;
      }

      AssetRepository repository = AssetUtil.getAssetRepository(false);

      if(!repository.containsEntry(def.getEntry())) {
         LOG.error("Viewsheet doesn't exist, MV creation ignored: " + def.getEntry());
         return false;
      }

      try {
         boolean rc = create0();
         def.setSuccess(rc);

         return rc;
      }
      catch(CancelledException ex) {
         def.setSuccess(false);
         throw ex;
      }
      catch(Exception ex) {
         def.setSuccess(false);
         throw new RuntimeException("Materialization of " +
                                       (def.isWSMV() ?
                                        "worksheet '" + def.getWsPath() :
                                        "viewsheet '" + def.getVsPath()) +
                                       "' failed: " + ex.getMessage(), ex);
      }
   }

   protected MVDef def;

   private static final Logger LOG = LoggerFactory.getLogger(AbstractMVCreator.class);
}
