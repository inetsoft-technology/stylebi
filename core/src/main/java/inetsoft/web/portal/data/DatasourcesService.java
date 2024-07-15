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
package inetsoft.web.portal.data;

import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.uql.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasourcesService extends DatasourcesBaseService {
   @Autowired
   public DatasourcesService(XRepository repository,
                             SecurityProvider securityProvider)
   {
      super(repository, securityProvider);
   }

   /**
    * Create a new data source connection.
    *
    * @param definition new data source definition.
    * @param ds existing data source if updating (not new).
    * @return data source object for the new connection
    */
   @Override
   public XDataSource createDataSource(BaseDataSourceDefinition definition, XDataSource ds) {
      return createDataSource((DataSourceDefinition) definition, ds);
   }

   /**
    * Create a new data source connection.
    *
    * @param definition new data source definition.
    * @param ds existing data source if updating (not new).
    * @return data source object for the new connection
    */
   private XDataSource createDataSource(DataSourceDefinition definition, XDataSource ds) {
      checkDatasourceNameValid(ds == null ? null : ds.getName(), definition.getName(),
         definition.getParentPath());

      if(ds == null) {
         String dsClass = Config.getDataSourceClass(definition.getType());

         try {
            ds = (XDataSource) Config.getClass(definition.getType(), dsClass)
               .getConstructor().newInstance();
         }
         catch(Exception e) {
            LOG.error("Failed to create class: {} ({})", dsClass, definition.getType(), e);
         }
      }

      if(ds != null) {
         TabularUtil.refreshView(definition.getTabularView(), ds);
         String parentPath = "";

         if((definition.getParentDataSource() == null ||
            definition.getParentDataSource().isEmpty()) &&
            definition.getParentPath() != null && !definition.getParentPath().isEmpty() &&
            !"/".equals(definition.getParentPath()))
         {
            parentPath = definition.getParentPath() + "/";
         }

         String sourceName = "/".equals(parentPath) ? definition.getName() : parentPath + definition.getName();
         ds.setName(sourceName);
         ds.setDescription(definition.getDescription());
      }

      return ds;
   }

   private static final Logger LOG = LoggerFactory.getLogger(DatasourcesService.class);
}
