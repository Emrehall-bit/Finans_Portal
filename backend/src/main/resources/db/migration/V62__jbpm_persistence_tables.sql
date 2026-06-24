-- jBPM 7 persistence tables (PostgreSQL)
-- SessionInfo, ProcessInstanceInfo, WorkItemInfo + correlation tables
-- hibernate_sequence required for GenerationType.AUTO with Hibernate 6 legacy naming strategy

CREATE SEQUENCE IF NOT EXISTS hibernate_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS SessionInfo (
    id                   BIGINT       NOT NULL,
    lastModificationDate TIMESTAMP,
    rulesByteArray       BYTEA,
    startDate            TIMESTAMP,
    OPTLOCK              INTEGER,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS ProcessInstanceInfo (
    instanceId               BIGINT       NOT NULL,
    lastMod                  TIMESTAMP,
    lastReadDate             TIMESTAMP,
    processId                VARCHAR(255),
    processInstanceByteArray BYTEA,
    startDate                TIMESTAMP,
    state                    INTEGER      NOT NULL,
    OPTLOCK                  INTEGER,
    PRIMARY KEY (instanceId)
);

CREATE TABLE IF NOT EXISTS EventTypes (
    InstanceId BIGINT      NOT NULL,
    element    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS WorkItemInfo (
    workItemId        BIGINT       NOT NULL,
    creationDate      TIMESTAMP,
    name              VARCHAR(255),
    processInstanceId BIGINT,
    state             BIGINT       NOT NULL,
    OPTLOCK           INTEGER,
    workItemByteArray BYTEA,
    PRIMARY KEY (workItemId)
);

CREATE TABLE IF NOT EXISTS CorrelationKeyInfo (
    keyId             BIGINT       NOT NULL,
    deploymentId      VARCHAR(255),
    name              VARCHAR(255),
    processInstanceId BIGINT,
    OPTLOCK           INTEGER,
    PRIMARY KEY (keyId)
);

CREATE TABLE IF NOT EXISTS CorrelationPropertyInfo (
    id                        BIGINT       NOT NULL,
    name                      VARCHAR(255),
    value                     VARCHAR(255),
    correlationKey_keyId      BIGINT,
    OPTLOCK                   INTEGER,
    PRIMARY KEY (id)
);

ALTER TABLE EventTypes
    ADD CONSTRAINT fk_eventtypes_processinstance
    FOREIGN KEY (InstanceId) REFERENCES ProcessInstanceInfo(instanceId);

ALTER TABLE CorrelationPropertyInfo
    ADD CONSTRAINT fk_corrprop_corrkey
    FOREIGN KEY (correlationKey_keyId) REFERENCES CorrelationKeyInfo(keyId);
