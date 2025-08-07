CREATE TABLE INETSOFT_ORG (
  ORG_ID VARCHAR(255) PRIMARY KEY,
  ORG_NAME VARCHAR(255) NOT NULL);

CREATE TABLE INETSOFT_USER (
  ORG_ID VARCHAR(255) NOT NULL,
  USER_NAME VARCHAR(255) NOT NULL,
  EMAIL VARCHAR(255),
  PW_HASH VARCHAR(255) NOT NULL,
  PRIMARY KEY (ORG_ID, USER_NAME),
  FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));

CREATE TABLE INETSOFT_ROLE (
  ORG_ID VARCHAR(255) NOT NULL,
  ROLE_NAME VARCHAR(255) NOT NULL,
  PRIMARY KEY (ORG_ID, ROLE_NAME),
  FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));

CREATE TABLE INETSOFT_GROUP (
  ORG_ID VARCHAR(255) NOT NULL,
  GROUP_NAME VARCHAR(255) NOT NULL,
  PRIMARY KEY (ORG_ID, GROUP_NAME),
  FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID));

CREATE TABLE INETSOFT_USER_ROLE (
  ORG_ID VARCHAR(255) NOT NULL,
  USER_NAME VARCHAR(255) NOT NULL,
  ROLE_NAME VARCHAR(255),
  PRIMARY KEY (ORG_ID, USER_NAME, ROLE_NAME),
  FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID),
  FOREIGN KEY (ORG_ID, USER_NAME) REFERENCES INETSOFT_USER (ORG_ID, USER_NAME),
  FOREIGN KEY (ORG_ID, ROLE_NAME) REFERENCES INETSOFT_ROLE (ORG_ID, ROLE_NAME));

CREATE TABLE INETSOFT_GROUP_USER (
  ORG_ID VARCHAR(255) NOT NULL,
  GROUP_NAME VARCHAR(255) NOT NULL,
  USER_NAME VARCHAR(255),
  PRIMARY KEY (ORG_ID, GROUP_NAME, USER_NAME),
  FOREIGN KEY (ORG_ID) REFERENCES INETSOFT_ORG (ORG_ID),
  FOREIGN KEY (ORG_ID, GROUP_NAME) REFERENCES INETSOFT_GROUP (ORG_ID, GROUP_NAME),
  FOREIGN KEY (ORG_ID, USER_NAME) REFERENCES INETSOFT_USER (ORG_ID, USER_NAME));

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('host-org', 'Host Organization');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('host-org', 'admin', 'admin@inetsoft.com', '$2a$10$9DAdlDEgv.b46eABrpyWou7vhjEx3kCjcJ4hsj18iOnAW8PfxdeN6');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('host-org', 'Site Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('host-org', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('host-org', 'admin', 'Site Admin');

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('bauch-waters', 'Bauch-Waters');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'syreeta.graham', 'francesco.hodkiewicz@hotmail.com', '$2a$10$ERjbVFFP62DIUNEDzvh/CeZlty8DJ9DErIwE/zI4l7S39813xj6wu');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'maria.koelpin', 'mandy.prosacco@gmail.com', '$2a$10$8QPP/NRY5xIhpQQ7X7l7AeAA3aWNHGRa/LiwVP7Y57WobD5wAkTcu');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'vaughn.kozey', 'ted.langosh@yahoo.com', '$2a$10$nra5Iyz7bKQ6a5xONG5.seEmbzeb4ToRcdI3CGjno.icXTO.YE7Za');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'anette.smitham', 'jeniffer.ryan@gmail.com', '$2a$10$fVMUTjx4PotayXPGMNjWu.GIbBGIaWxcG80fO6FuOjdn9zzhUa3Yq');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'isadora.orn', 'scottie.schneider@hotmail.com', '$2a$10$0GvhTB.h8PN5.UOV09pxf.LYoW6r7zsgMhS3PwmphOydGNaAjqmaG');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'lorretta.langworth', 'domonique.prosacco@yahoo.com', '$2a$10$ueDhDsHrRuM8K1tI4opyluDBIYWQK5dN8wJPtcI0U3jqgDLV11v2i');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'mollie.dooley', 'rhoda.boyer@yahoo.com', '$2a$10$OzKZXqyq4Pu3lwzqXQLQVOxUC9BdgkYpxMOuMucoZr5plF4OgWOqS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'yukiko.boyer', 'sandee.schiller@hotmail.com', '$2a$10$vZzuXwpPDepeWvDxYQ6Ruey6Gm2Ok.8.W0ijHEh1svjLUoGCwPd6m');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'pattie.crooks', 'roscoe.kuhlman@gmail.com', '$2a$10$VxF/06isO7ObSw..fhPEV.SdU5EPaQFrbyWg74T3o7o03LiLCnyZu');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'matthew.west', 'krystyna.marquardt@hotmail.com', '$2a$10$DdCVTTxta7lEYW66/a5lZOFo8yJMWp/R911fxByI45qWYx3FV7u6K');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'rodrick.satterfield', 'buford.hagenes@gmail.com', '$2a$10$4rT/AKzJYfxtOeN1cvNxweQUyvLS.qUeBRlP.YHXrCjAlg1MFl6am');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'evia.boehm', 'penny.greenholt@hotmail.com', '$2a$10$KWFcK4NHKsDSgAI0JukdFOEXyTdgHj4/SxLaBzSCYX9cwRgeue9uW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bauch-waters', 'olen.steuber', 'rosamond.bergnaum@gmail.com', '$2a$10$lK5.gFkQh.D6I4dVFQPdpef/AuLNFHinEnyyBgdPNQ5o3UocrAOdG');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bauch-waters', 'Org Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bauch-waters', 'Director');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bauch-waters', 'Administrator');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bauch-waters', 'Coordinator');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bauch-waters', 'Officer');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bauch-waters', 'Development');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bauch-waters', 'Legal');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bauch-waters', 'Shipping');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'syreeta.graham', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'maria.koelpin', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'maria.koelpin', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'vaughn.kozey', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'vaughn.kozey', 'Officer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'anette.smitham', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'anette.smitham', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'isadora.orn', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'isadora.orn', 'Officer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'lorretta.langworth', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'lorretta.langworth', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'mollie.dooley', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'mollie.dooley', 'Officer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'yukiko.boyer', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'yukiko.boyer', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'pattie.crooks', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'pattie.crooks', 'Officer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'matthew.west', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'matthew.west', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'rodrick.satterfield', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'rodrick.satterfield', 'Officer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'evia.boehm', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'evia.boehm', 'Administrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'olen.steuber', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bauch-waters', 'olen.steuber', 'Officer');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Legal', 'vaughn.kozey');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Legal', 'lorretta.langworth');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Legal', 'pattie.crooks');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Legal', 'evia.boehm');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Development', 'maria.koelpin');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Development', 'isadora.orn');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Development', 'yukiko.boyer');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Development', 'rodrick.satterfield');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Shipping', 'anette.smitham');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Shipping', 'mollie.dooley');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Shipping', 'matthew.west');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bauch-waters', 'Shipping', 'olen.steuber');

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('trantow-walter-and-davis', 'Trantow, Walter and Davis');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'donnie.veum', 'silvia.buckridge@hotmail.com', '$2a$10$QokD8f9KvUj2ZchUBu0kzelrwS9di4tSKnw3J/frNSzVCOb1ue.46');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'delbert.ruecker', 'deirdre.lesch@yahoo.com', '$2a$10$tWbeJBNgzu2k.FvZqBA4COHTTVfUWA9NWQTv4vsJA1IdKM/M8ghQ2');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'aleisha.smitham', 'chi.reichel@gmail.com', '$2a$10$9J4KO28Kl5Zv8ezIYGTVFOVIvwvrJz4qkjbgSk6XoFMH2TE9cy7NK');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'jammie.volkman', 'vi.adams@gmail.com', '$2a$10$PAGoevarx7FK02a0dU/d6OyS28GEDGidBE6WakgA3qu.E1SwutHYm');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'donte.hoppe', 'heike.fisher@hotmail.com', '$2a$10$Bma7MCzmclkypw1.aOPRFuSlel2bIdn0Ofgl0EuGkU4gCikOCyRSW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'lloyd.bernier', 'everett.schultz@hotmail.com', '$2a$10$KN1k9wAB2v2E7f8S2nE9a.Y36LTpUTuyxUH1VVjHH3AfIgwVEn4xy');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'valda.zboncak', 'mauricio.cummings@gmail.com', '$2a$10$Io4Gi5maGvmL5YXJEpDKTODMKKu4qbHAgLE8S6y2...qH1hDU.0GC');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'francesca.rodriguez', 'kathie.romaguera@hotmail.com', '$2a$10$tfQX73Ztx9UhKrEyfdYT5.zKTnlfUOzjU/PDsm6/LYkVt7pWdtNXm');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'hermelinda.leffler', 'mira.heidenreich@hotmail.com', '$2a$10$rbPBDjwVF2OugktkkRwRwumLR1EcbT.78pr.ouxQvkzjC72ztxcwe');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'maranda.nader', 'tonie.conn@gmail.com', '$2a$10$eW62WEd2/Pd2exlBKQRRk.d21pCRrIMOs/eovG4wsG7zWYpDG7/h2');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'nolan.kling', 'wm.treutel@hotmail.com', '$2a$10$L8ieZUgUdE./GUb6R/D.weRf69fPKKhuh4IpTcQmNRT7qdiMP.LyS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'sherman.grant', 'gavin.ritchie@gmail.com', '$2a$10$uCjMBwRVCipf8R0bseWMR.LudEWLKGAb3xyyPS9n49Q/V7mEfpRJe');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('trantow-walter-and-davis', 'carolyne.friesen', 'iris.boyer@hotmail.com', '$2a$10$APNshvLy7GmyQXN9A/2t6./TQGkVou3ZXJXUS4gWTaxqq6nwWqk6y');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'Org Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'Liaison');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'Manager');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'Director');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'Executive');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('trantow-walter-and-davis', 'Legal');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('trantow-walter-and-davis', 'QA');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('trantow-walter-and-davis', 'Marketing');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'donnie.veum', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'delbert.ruecker', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'delbert.ruecker', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'aleisha.smitham', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'aleisha.smitham', 'Executive');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'jammie.volkman', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'jammie.volkman', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'donte.hoppe', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'donte.hoppe', 'Executive');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'lloyd.bernier', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'lloyd.bernier', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'valda.zboncak', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'valda.zboncak', 'Executive');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'francesca.rodriguez', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'francesca.rodriguez', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'hermelinda.leffler', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'hermelinda.leffler', 'Executive');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'maranda.nader', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'maranda.nader', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'nolan.kling', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'nolan.kling', 'Executive');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'sherman.grant', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'sherman.grant', 'Manager');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'carolyne.friesen', 'Director');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('trantow-walter-and-davis', 'carolyne.friesen', 'Executive');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'QA', 'aleisha.smitham');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'QA', 'lloyd.bernier');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'QA', 'hermelinda.leffler');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'QA', 'sherman.grant');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Legal', 'delbert.ruecker');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Legal', 'donte.hoppe');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Legal', 'francesca.rodriguez');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Legal', 'nolan.kling');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Marketing', 'jammie.volkman');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Marketing', 'valda.zboncak');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Marketing', 'maranda.nader');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('trantow-walter-and-davis', 'Marketing', 'carolyne.friesen');

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('jenkins-bednar', 'Jenkins-Bednar');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'shanita.trantow', 'marisela.botsford@hotmail.com', '$2a$10$G5T.a3dgHRrXeAIU5y45W.qusUDuqL//1SM9H0bQ3WoLJn1LSaoOG');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'marcel.littel', 'virgen.hahn@yahoo.com', '$2a$10$srVKN/m1v092PL4bggBOaO1XxIRzxoUMm7gWp47XGoLs4ByH.J0nO');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'coletta.pfannerstill', 'jackson.hintz@gmail.com', '$2a$10$2E3kGcx8N9W7ZcFtjoyj4O8iuCUDSIqSLdDFvFKWByH8C2yD7aBEi');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'jamie.kessler', 'sheena.fritsch@hotmail.com', '$2a$10$YrI11iMGIoKYa/FsVdt5KeQRkDiSGJSvARQfjTSJEFQA2ktLPPhs.');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'roy.gibson', 'adolph.kuhlman@gmail.com', '$2a$10$lB/hQ.0b7wzwT8uOXhxa2.0h1Q2CORGvTRMBwlIhEE2WmkOj4O5la');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'michal.jaskolski', 'allison.gorczany@yahoo.com', '$2a$10$0MTXmtYfXoqB7jNDxXsdS.B5BVgvYNqWNcr3n7NKkvYsUJezIGzYC');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'wilda.leuschke', 'carrol.parisian@gmail.com', '$2a$10$T.QLE8ef/ghRwuL/PFU1lOBYdxTlEk6gU3w4KFz2t/2dLGTXcrbOK');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'antione.wiza', 'fidel.breitenberg@hotmail.com', '$2a$10$tKHuZfTPY9GNsj/QDuevOe2x1o/1LTMFfkejGiUkBpZzF0zkH3WAC');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'wendi.skiles', 'abdul.weber@hotmail.com', '$2a$10$gNDfXFFyg6R/R/YUQzkRAeZH8wcejZM59hf63ABOW2xvrShJ8gIfO');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'cher.powlowski', 'chere.howe@hotmail.com', '$2a$10$G5E1S3GC30pm46U2X.Cv1e9WQvnUpw6ipM5Wul8bJGGE78KPe1eCW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'bethann.swift', 'major.schamberger@hotmail.com', '$2a$10$Iq2S4CBgbwBMyDSyvIySXehrWtJwEkzyk8M3IIH90S8THLoM5bLkG');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'leigh.rosenbaum', 'manuela.stark@hotmail.com', '$2a$10$dCmGwWvzgopaSYn36y/26ucuhtTy4yI1T9bDgxfE9xCnfMx0yKZnW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('jenkins-bednar', 'etta.jerde', 'maryln.larson@yahoo.com', '$2a$10$e7fP9ahF6LaqQ.BaC8Wvh.eiaQc9/w4y7PdcCDEJhlOla6xeybC0u');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('jenkins-bednar', 'Org Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('jenkins-bednar', 'Associate');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('jenkins-bednar', 'Agent');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('jenkins-bednar', 'Engineer');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('jenkins-bednar', 'Coordinator');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('jenkins-bednar', 'Legal');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('jenkins-bednar', 'IT');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('jenkins-bednar', 'Finance');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'shanita.trantow', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'marcel.littel', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'marcel.littel', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'coletta.pfannerstill', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'coletta.pfannerstill', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'jamie.kessler', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'jamie.kessler', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'roy.gibson', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'roy.gibson', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'michal.jaskolski', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'michal.jaskolski', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'wilda.leuschke', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'wilda.leuschke', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'antione.wiza', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'antione.wiza', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'wendi.skiles', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'wendi.skiles', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'cher.powlowski', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'cher.powlowski', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'bethann.swift', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'bethann.swift', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'leigh.rosenbaum', 'Associate');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'leigh.rosenbaum', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'etta.jerde', 'Engineer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('jenkins-bednar', 'etta.jerde', 'Coordinator');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Finance', 'jamie.kessler');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Finance', 'wilda.leuschke');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Finance', 'cher.powlowski');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Finance', 'etta.jerde');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Legal', 'marcel.littel');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Legal', 'roy.gibson');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Legal', 'antione.wiza');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'Legal', 'bethann.swift');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'IT', 'coletta.pfannerstill');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'IT', 'michal.jaskolski');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'IT', 'wendi.skiles');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('jenkins-bednar', 'IT', 'leigh.rosenbaum');

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('bins-quigley-and-schamberger', 'Bins, Quigley and Schamberger');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'shayne.schiller', 'alpha.koelpin@yahoo.com', '$2a$10$y3HpAOgiQVGXxV0DUaapGudQYWZgg1ZCofxmVd98u3ark2iKRAQFS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'minh.rolfson', 'demetria.bode@yahoo.com', '$2a$10$FUxliEelJT9XOPomuuetAOZCykbr03QnzU43I9yRdA/EUPK1.dKQi');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'antwan.leannon', 'alva.kling@gmail.com', '$2a$10$bTyw5b2LMa8mqdsDaUY1JuL4P5L33pgv9mbKyfw/1A4VtgmySOJJS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'leesa.braun', 'anibal.sauer@yahoo.com', '$2a$10$9qUo1jB7Axze1Hym9/T9puOK8VaHv9b8BfAV9s8SeS3fDUvWaMtGK');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'donald.rodriguez', 'edmond.rohan@yahoo.com', '$2a$10$E2/T2dGW3GOJpEUsFze2dONs300Gh.I9Ng.Y1dnKK9zNgn9u.xy.y');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'mitchell.lemke', 'tressie.satterfield@yahoo.com', '$2a$10$UOuOXKb/tH9HUYmhoW8o5uXXC3OpfVEHq2/iL4Xz9ZILCMwYKMGP6');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'jefferson.bosco', 'ozzie.leannon@hotmail.com', '$2a$10$f0XV4I8enVNkNJaxR.oP.OZoAloq7ugBFzUoy739DlfQhYmHxQFZO');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'lawrence.wehner', 'santa.nienow@gmail.com', '$2a$10$WSavRZD9rbzzWcbqkFQ7IOWr1e9Jn60hzWUaLaulgdUMtO.uX9rfy');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'andrew.roberts', 'necole.stehr@gmail.com', '$2a$10$ZozTruDTbP9U0lFYwILm1u6BPK/ln.AR.v7c3FPoYrCQzPcCA.IQK');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'jetta.parisian', 'ernest.wolf@gmail.com', '$2a$10$Nyfbkxh5hymkxHRJwAFV/eXpGFakRf38KYByvkT7kYEkuAk7lHu3i');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'felix.barton', 'rueben.kerluke@hotmail.com', '$2a$10$zNTt3denQP6XfFuaxNypxO2aAY0vtGlmX.wZWsqkSULNc1Rapn5MS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'sheila.upton', 'carey.harber@hotmail.com', '$2a$10$6QRz8OAIe37oNmGyx4Fud.311/GGmvo9.eetVVEK4BV5EJ0eHcA/y');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('bins-quigley-and-schamberger', 'tamisha.turcotte', 'lael.nolan@hotmail.com', '$2a$10$aoObf7v4GhZFvJB7H3J3VuEkfXz5Vm8OM6MdLFTOwZmTGwcqA4EVC');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'Org Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'Coordinator');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'Assistant');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'Orchestrator');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'Facilitator');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bins-quigley-and-schamberger', 'Shipping');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bins-quigley-and-schamberger', 'QA');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('bins-quigley-and-schamberger', 'Legal');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'shayne.schiller', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'minh.rolfson', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'minh.rolfson', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'antwan.leannon', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'antwan.leannon', 'Facilitator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'leesa.braun', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'leesa.braun', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'donald.rodriguez', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'donald.rodriguez', 'Facilitator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'mitchell.lemke', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'mitchell.lemke', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'jefferson.bosco', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'jefferson.bosco', 'Facilitator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'lawrence.wehner', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'lawrence.wehner', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'andrew.roberts', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'andrew.roberts', 'Facilitator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'jetta.parisian', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'jetta.parisian', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'felix.barton', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'felix.barton', 'Facilitator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'sheila.upton', 'Coordinator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'sheila.upton', 'Assistant');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'tamisha.turcotte', 'Orchestrator');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('bins-quigley-and-schamberger', 'tamisha.turcotte', 'Facilitator');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'QA', 'antwan.leannon');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'QA', 'mitchell.lemke');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'QA', 'andrew.roberts');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'QA', 'sheila.upton');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Legal', 'leesa.braun');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Legal', 'jefferson.bosco');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Legal', 'jetta.parisian');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Legal', 'tamisha.turcotte');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Shipping', 'minh.rolfson');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Shipping', 'donald.rodriguez');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Shipping', 'lawrence.wehner');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('bins-quigley-and-schamberger', 'Shipping', 'felix.barton');

INSERT INTO INETSOFT_ORG (ORG_ID, ORG_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Powlowski, Bernier and Romaguera');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'clemente.ankunding', 'teressa.leannon@yahoo.com', '$2a$10$/N7dhU0DgF0RqUaNL6W3Jupp1aZR/AqdthI/xvGVT6aXHj0iubOga');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'marcelino.schimmel', 'emmanuel.koepp@yahoo.com', '$2a$10$YlxXcmoG1OyeyaWZETj3S.rI8ONTPOP26/MEQaT1Xk2Nz7N4BQva.');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'janna.windler', 'clair.bergstrom@gmail.com', '$2a$10$Zp4i6KOyeBYCxppFQTl8WOdh3Bnt4vdoXlDyGS.aog/KwSsBNVvg2');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'jeannine.leffler', 'ronna.ondricka@hotmail.com', '$2a$10$8kgHXF7F1FmMBxKaXdNDJuFi5uQAsie2r4TpDCd7hdzJV50aGICCW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'edward.wiza', 'larry.metz@yahoo.com', '$2a$10$u6bGBs6yDKjlvHC72WZRTO/LfmQ6zjEFyr.bZ4f8ngs3c0ISDpO9G');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'lanita.littel', 'dante.dicki@gmail.com', '$2a$10$4enrQJ/JrNY3c/5uCdesfOghjyUFS/FtY52fnW235DfjpKEjEOhCa');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'ariel.mann', 'floria.vonrueden@yahoo.com', '$2a$10$amgjhVhert66t25Ee2NFhe/y3rxMyzZWEtTsBwxy8/wn1MbtTGYzK');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'marshall.parker', 'arthur.stamm@yahoo.com', '$2a$10$sBP2AflnBVdJHl0cx9Kq0ukknFWvAxnS/KvwvnyBh3eiE2kOp3GvS');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'nelia.cartwright', 'gil.willms@gmail.com', '$2a$10$KvfBWZ.ZukWx1/JjbVEZh.GK/Nz0Gm1cSmp6/HwaR94JX0HGJc/pW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'joan.hahn', 'kareen.boyer@gmail.com', '$2a$10$fpuyMjUGgoIjiwC2uxbZfOWSR3Ak7BqIH0ZO1LLpi08GSjuYw7wiC');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'alisia.nitzsche', 'alfreda.satterfield@hotmail.com', '$2a$10$KSGv0gdjDLLWL/mN9k5SOeibSKI1pYXXiMmv1AXCWNLQbmy/V9V8a');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'mel.reichert', 'simon.hegmann@hotmail.com', '$2a$10$Y190P5VAgdfVeXg7rgpFceimDIWxiab2YbHf5H4OVQGLlTEZotKyW');
INSERT INTO INETSOFT_USER (ORG_ID, USER_NAME, EMAIL, PW_HASH) VALUES ('powlowski-bernier-and-romaguera', 'leeanne.ruecker', 'sanjuanita.dibbert@hotmail.com', '$2a$10$MZsS.QLascg1XYgdlwvxGuT9DXbl3HHzoJa1DGtdOax/hO0sIQItm');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Org Admin');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Agent');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Analyst');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Liaison');
INSERT INTO INETSOFT_ROLE (ORG_ID, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Designer');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Purchasing');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Procurement');
INSERT INTO INETSOFT_GROUP (ORG_ID, GROUP_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Accounting');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'clemente.ankunding', 'Org Admin');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'marcelino.schimmel', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'marcelino.schimmel', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'janna.windler', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'janna.windler', 'Designer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'jeannine.leffler', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'jeannine.leffler', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'edward.wiza', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'edward.wiza', 'Designer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'lanita.littel', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'lanita.littel', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'ariel.mann', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'ariel.mann', 'Designer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'marshall.parker', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'marshall.parker', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'nelia.cartwright', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'nelia.cartwright', 'Designer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'joan.hahn', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'joan.hahn', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'alisia.nitzsche', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'alisia.nitzsche', 'Designer');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'mel.reichert', 'Agent');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'mel.reichert', 'Analyst');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'leeanne.ruecker', 'Liaison');
INSERT INTO INETSOFT_USER_ROLE (ORG_ID, USER_NAME, ROLE_NAME) VALUES ('powlowski-bernier-and-romaguera', 'leeanne.ruecker', 'Designer');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Procurement', 'janna.windler');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Procurement', 'lanita.littel');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Procurement', 'nelia.cartwright');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Procurement', 'mel.reichert');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Purchasing', 'marcelino.schimmel');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Purchasing', 'edward.wiza');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Purchasing', 'marshall.parker');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Purchasing', 'alisia.nitzsche');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Accounting', 'jeannine.leffler');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Accounting', 'ariel.mann');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Accounting', 'joan.hahn');
INSERT INTO INETSOFT_GROUP_USER (ORG_ID, GROUP_NAME, USER_NAME) VALUES ('powlowski-bernier-and-romaguera', 'Accounting', 'leeanne.ruecker');

