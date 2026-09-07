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
db.createCollection("table1", {company: "AT&T", state: "NJ", web: "www.att.com", revenue: 123000, phone: "732-123-8899"});
db.table1.insert({company: "AT&T", state: "NJ", web: "www.att.com", revenue: 123000, phone: "732-123-8899"});
db.table1.insert({company: "IBM", state: "NY", web: "www.ibm.com", revenue: 21099, phone: "212-388-8211"});

// Fixtures for MongoCatalog (TabularCatalogProvider): heterogeneous documents so listDatasets/
// describeDataset can be exercised against a real server, not just mocks.
db.createCollection("catalog_shapes");
// Present in every document, so it is a safe stand-in for a guaranteed key column.
db.catalog_shapes.insert({name: "first", qty: NumberInt(3), active: true, when: new Date(0)});
// "onlyInSecond" never appears in the first document -- exercises the key-union-across-samples
// path. "qty" here is a NumberLong, not a NumberInt as above -- exercises "first non-null value
// in scan order decides the column's type" for a key whose real type varies across documents.
db.catalog_shapes.insert({name: "second", qty: NumberLong(5), onlyInSecond: "x"});

// Exists but holds zero documents -- describeDataset must throw, not fabricate a column list.
db.createCollection("catalog_empty");

// GridFS's own naming convention: a legal MongoDB collection name containing '.', which
// TabularDatasetRef.id's contract forbids. listDatasets must exclude it without failing to list
// every other collection in the database.
// Not left empty: a describeDataset test needs to tell "rejected because of the dot" apart from
// "rejected because there was nothing to sample", which requires a real document to sample.
db.createCollection("catalog_fs.files");
db.getCollection("catalog_fs.files").insert({filename: "a.txt", length: NumberInt(10)});

// db.createUser({ user: "root", pwd: "password", roles: [ { role: "userAdminAnyDatabase", db: "admin" } ] });
db.createUser({ user: "test", pwd: "password", roles: [ { role: "readWrite", db: "test" } ] });
db.createUser({ user: "user1", pwd: "password", roles: [ { role: "readWrite", db: "test" } ] });
