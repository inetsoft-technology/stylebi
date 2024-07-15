/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.recommender.WizardRecommenderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.concurrent.locks.*;

@Service
public class VSWizardTemporaryInfoService {
   @Autowired
   public VSWizardTemporaryInfoService(ViewsheetService viewsheetService)
   {
      this.viewsheetService = viewsheetService;
   }

   /**
    * Init temporaryInfo should be done before opening the object wizard pane,
    * it used to store the tree information and objects recommandation informations.
    *
    * @param  vsId      the runtime viewsheet id.
    * @param  principal current user.
    * @param  position  position of the target object in grid pane.
    *
    * @see #getVSTemporaryInfo
    */
   public void initTemporary(String vsId, Principal principal, Point position) throws Exception {
      TEMP_INFO_LOCK.writeLock().lock();

      try {
         RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
         Viewsheet vs = rvs.getViewsheet();
         VSTemporaryInfo temporaryInfo = rvs.getVSTemporaryInfo();

         if(temporaryInfo == null || temporaryInfo.isDestroyed()) {
            // create temporary info
            temporaryInfo = new VSTemporaryInfo();
         }

         temporaryInfo.setPosition(position);
         // create temp chart
         ChartVSAssembly tempChart = new ChartVSAssembly(vs, VSWizardConstants.TEMP_CHART_NAME);
         tempChart.getVSAssemblyInfo().setWizardTemporary(true);
         vs.addAssembly(tempChart);
         temporaryInfo.setTempChart(tempChart);

         // if creating temporaryInfo, setting it to rvs when temporaryInfo has prepared.
         rvs.setVSTemporaryInfo(temporaryInfo);
      }
      finally {
         TEMP_INFO_LOCK.writeLock().unlock();
      }
   }

   /**
    * Check if the target viewsheet exist temporary information created for viewsheet wizard.
    */
   public boolean existTemporary(String vsId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();

      return this.getVSTemporaryInfo(rvs) != null
         || vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME) != null
         || vs.getAssembly(VSWizardConstants.TEMP_CROSSTAB_NAME) != null
         || vs.getLatestTempAssembly() != null;
   }

   /**
    * Remove the VSTemporaryInfo and all temporary assemblies,
    * this should be done after leaving the object wizard pane.
    */
   public void destroyTemporary(String vsId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);

      if(rvs == null) {
         LOGGER.info("Clean temp failed: Sheet is not exist!");
         return;
      }

      Viewsheet vs = rvs.getViewsheet();
      // only destroy temporary binding info when close object wizard,
      // the whole temporaryinfo should only be destroyed when close wizard pane.
      VSTemporaryInfo tempInfo = this.getVSTemporaryInfo(rvs);

      if(tempInfo != null) {
         tempInfo.destroyTempBindingInfo();
      }

      // destroy temp chart
      vs.removeAssembly(VSWizardConstants.TEMP_CHART_NAME);
      // destroy temp crosstab
      vs.removeAssembly(VSWizardConstants.TEMP_CROSSTAB_NAME);
      // destroy temp primary assembly. need to update sandbox info.
      destroyTempAssembly(rvs);
   }

   /**
    * Destroy all temp assembly.
    */
   public void destroyTempAssembly(RuntimeViewsheet rvs) {
      assert rvs != null;
      Viewsheet vs = rvs.getViewsheet();
      Assembly[] assemblys = vs.getAssemblies(false, false, true);

      for(Assembly tempAssembly: assemblys) {
         if(WizardRecommenderUtil.isTempAssembly(tempAssembly.getName())) {
            vs.removeAssembly(tempAssembly.getName(), false);
         }
      }
   }

   /**
    * Destroy expired temp assembly.
    */
   public void destroyExpiredTempAssmbly(RuntimeViewsheet rvs) {
      if(EXPIRED_TEMP_ASSEMBLY_LOCK.tryLock()) {
         try {
            Viewsheet vs = rvs.getViewsheet();
            VSAssembly latestTempAssembly = WizardRecommenderUtil.getTempAssembly(vs);

            if(latestTempAssembly == null) {
               return;
            }

            Assembly[] assemblies = vs.getAssemblies(false, false, true);
            long currentTime = System.currentTimeMillis();

            for(Assembly assembly: assemblies) {
               String name = assembly.getName();

               if(WizardRecommenderUtil.isTempAssembly(name) && assembly != latestTempAssembly) {
                  long time =
                     Long.parseLong(name.split(VSWizardConstants.TEMP_ASSEMBLY_SEPARATOR)[1]);

                  if(currentTime - time > VSWizardConstants.TEMP_ASSEMBLY_EXPIRED_TIME) {
                     vs.removeAssembly(assembly.getAbsoluteName(), false);
                  }
               }
            }
         }
         catch(Exception e) {
            LOGGER.error("Destroy expired temporary assembly failed.");
         }
         finally {
            EXPIRED_TEMP_ASSEMBLY_LOCK.unlock();
         }
      }
   }

   /**
    * Return the temporary chart assembly which used to store the tree information,
    * like dimensions, aggregates, geoRefs.
    */
   public ChartVSAssembly getTempChart(String vsId, Principal principal) throws Exception {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(vsId, principal);
      Viewsheet vs = rvs.getViewsheet();
      return (ChartVSAssembly) vs.getAssembly(VSWizardConstants.TEMP_CHART_NAME);
   }

   /**
    * Because the websoket events are asynchronous, when opening object wizard pane, binding tree
    * event maybe handled when temporary info init logic(by OpenWizardObjectEvent) not finished,
    * so fix by using read write lock.
    */
   public VSTemporaryInfo getVSTemporaryInfo(RuntimeViewsheet rvs) {
      VSTemporaryInfo vsTemporaryInfo = rvs.getVSTemporaryInfo();

      if(vsTemporaryInfo == null) {
         TEMP_INFO_LOCK.readLock().lock();

         try {
            vsTemporaryInfo = rvs.getVSTemporaryInfo();
         }
         finally {
            TEMP_INFO_LOCK.readLock().unlock();
         }
      }

      return vsTemporaryInfo;
   }

   private final ViewsheetService viewsheetService;
   private final Lock EXPIRED_TEMP_ASSEMBLY_LOCK = new ReentrantLock();
   private final ReadWriteLock TEMP_INFO_LOCK = new ReentrantReadWriteLock();

   private static final Logger LOGGER = LoggerFactory.getLogger(VSWizardTemporaryInfoService.class);
}
