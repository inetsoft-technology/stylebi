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
package inetsoft.uql;

import com.google.auto.service.AutoService;
import inetsoft.uql.listing.*;

import java.util.Arrays;
import java.util.List;

@AutoService(DataSourceListingService.class)
public class JDBCListingService implements DataSourceListingService {
   @Override
   public List<DataSourceListing> getDataSourceListings() {
      // Following the order of inetsoft/uql/config.xml jdbc drivers
      return Arrays.asList(
         new JDBCDataSourceListing(),
         new MSAccessDataSourceListing(),
         new OracleDataSourceListing(),
         new SQLServerDataSourceListing(),
         new MySQLDataSourceListing(),
         new DB2DataSourceListing(),
         new JTDSDataSourceListing(),
         new IngresDataSourceListing(),
         new DerbyDataSourceListing(),
         new DerbyEmbeddedDataSourceListing(),
         new InformixDataSourceListing(),
         new SQLAnywhereDataSourceListing(),
         new PostgreSQLDataSourceListing(),
         new DremioDataSourceListing(),
         new ImpalaDataSourceListing(),
         new VerticaDataSourceListing(),
         new TeradataDataSourceListing(),
         new SAPHANADataSourceListing(),
         new SnowflakeDataSourceListing(),
         new ActianVectorDataSourceListing(),
         new AmazonAuroraMySQLDataSourceListing(),
         new AmazonAuroraPostgreSQLDataSourceListing(),
         new AmazonEMRDataSourceListing(),
         new AmazonRedshiftDataSourceListing(),
         new AsterDataNClusterDataSourceListing(),
         new CiscoInformationServerDataSourceListing(),
         new ExasolDataSourceListing(),
         new GoogleCloudSQLDataSourceListing(),
         new IBMAS400DataSourceListing(),
         new MapRDataSourceListing(),
         new MariaDBDataSourceListing(),
         new MemSQLDataSourceListing(),
         new MonetDBDataSourceListing(),
         new PivotalGreenplumDataSourceListing(),
         new ProgressOpenEdgeDataSourceListing(),
         new FileMakerDataSourceListing(),
         new LucidDBDataSourceListing(),
         new NetezzaDataSourceListing(),
         new SQLiteDataSourceListing(),
         new SQLServerExpressDataSourceListing(),
         new GoogleBigQueryDataSourceListing(),
         new SiebelCRMOracleDataSourceListing(),
         new SiebelCRMSQLServerDataSourceListing(),
         new HpccDataSourceListing(),
         new DrillDataSourceListing(),
         new PrestoDataSourceListing(),
         new PhoenixDataSourceListing()
      );
   }
}
