--
-- inetsoft-core - StyleBI is a business intelligence web application.
-- Copyright © 2024 InetSoft Technology (info@inetsoft.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with this program. If not, see <http://www.gnu.org/licenses/>.
--

/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */

/*
 * #%L
 * This file is part of StyleBI.
 * %%
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 * %%
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 * #L%
 */

/*
 * #%L
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 * %%
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 * #L%
 */

CREATE CACHED TABLE APP_USER (USER_ID BIGINT IDENTITY, USER_NAME VARCHAR(255), PASSWORD VARCHAR(255), EMAIL VARCHAR(255));
CREATE CACHED TABLE APP_ROLE (ROLE_ID BIGINT IDENTITY, ROLE_NAME VARCHAR(255));
CREATE CACHED TABLE APP_GROUP (GROUP_ID BIGINT IDENTITY, GROUP_NAME VARCHAR(255));
CREATE CACHED TABLE APP_USER_GROUP (USER_GROUP_ID BIGINT IDENTITY, USER_ID BIGINT, GROUP_ID BIGINT, UNIQUE (USER_ID, GROUP_ID), FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID), FOREIGN KEY (GROUP_ID) REFERENCES APP_GROUP (GROUP_ID));
CREATE CACHED TABLE APP_USER_ROLE (USER_ROLE_ID BIGINT IDENTITY, USER_ID BIGINT, ROLE_ID BIGINT, UNIQUE (USER_ID, ROLE_ID), FOREIGN KEY (USER_ID) REFERENCES APP_USER (USER_ID), FOREIGN KEY (ROLE_ID) REFERENCES APP_ROLE (ROLE_ID));

INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('vernagordon', '$2b$10$Y1yDWZIwO5dH3Kvi9EUqGeV0IVE0rxIcF6u3fLqdR5Ze99xGwbjtG', 'verna.gordon@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('arturostevenson', '$2b$10$HbcNowuQKWkVMrlQDC/nQOKHnCbD2aDAmItkEXMbDqV87hjYulVNe', 'arturo.stevenson@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('lloydwilson', '$2b$10$kjtICmOfNuupvxOfURzq.usQV0IcXRHO5aZij1ayb7zKq8XBE8C7a', 'lloyd.wilson@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('stevecurry', '$2b$10$WN847r6EOef9WextxDDNNuZSvJ6rzu1fbneOzRbpDKaEcR58OJPaq', 'steve.curry@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('ismaelhogan', '$2b$10$soPl01NiiojukioKPezaZOzrg7cbmC2syHrTfyB1.D0W.rP02LdxG', 'ismael.hogan@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('kurtgill', '$2b$10$wV4EvuWSZTOucHWCF6QDM.Az3oNeQeSxbnlKdxyPLHi2aLXu7G6dG', 'kurt.gill@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('bryanbell', '$2b$10$wO/XYtZPG4HZneKV13QWvO5jVIIgG3tPf8VL5tHYZMJs1ZFXyM/de', 'bryan.bell@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('krystalbrock', '$2b$10$5sjebEcdgDs.oN.jRWvVdO1tJec8N87xy3sPE2XYR3brcgFNy56..', 'krystal.brock@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('kirklamb', '$2b$10$/k2yNFXqkDl3CaR2d91uC.toS.3lF4ZVl8hN1ZfldNTobUzAyeoo6', 'kirk.lamb@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('charlieberry', '$2b$10$Y90rU6.GaUCQkqOCFfw4COVgpiV4gv6o8ED41H7VZlCwWCp4eWOZ.', 'charlie.berry@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('candacegriffin', '$2b$10$9W6EJidO5VAyPU8VK25Bi.FzteizdtXc8UMPLL5/osAOBAW4cE7Tq', 'candace.griffin@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('gladysweaver', '$2b$10$FwUTTWFQ60XrZlHcn/jK3uYHppzSq8HrpUMa7IyVpW6kDR1uzBebi', 'gladys.weaver@example.com');
INSERT INTO APP_USER (USER_NAME, PASSWORD, EMAIL) VALUES ('mariejones', '$2b$10$Q6dYm.aSgGMxgmgdPW8K1OLvWOefEScScIsYS2hlzbnY2mnGx1VwC', 'marie.jones@example.com');

INSERT INTO APP_GROUP (GROUP_NAME) VALUES ('Sales');
INSERT INTO APP_GROUP (GROUP_NAME) VALUES ('Marketing');
INSERT INTO APP_GROUP (GROUP_NAME) VALUES ('IT');
INSERT INTO APP_GROUP (GROUP_NAME) VALUES ('Support');

INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('Salesperson');
INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('Strategist');
INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('Developer');
INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('Manager');
INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('System Administrator');
INSERT INTO APP_ROLE (ROLE_NAME) VALUES ('Support Engineer');

INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (3, 1);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (3, 1);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (3, 4);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (4, 1);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (4, 1);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (5, 1);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (5, 1);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (6, 1);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (6, 1);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (7, 2);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (7, 2);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (7, 4);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (8, 2);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (8, 2);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (9, 2);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (9, 2);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (10, 2);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (10, 2);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (11, 3);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (11, 3);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (11, 4);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (11, 5);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (12, 3);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (12, 3);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (13, 3);
INSERT INTO APP_USER_GROUP (USER_ID, GROUP_ID) VALUES (13, 4);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (13, 3);
INSERT INTO APP_USER_ROLE (USER_ID, ROLE_ID) VALUES (13, 6);
