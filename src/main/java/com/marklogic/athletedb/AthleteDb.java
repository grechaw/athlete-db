package com.marklogic.athletedb;

import ch.qos.logback.core.status.Status;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.datamovement.*;
import com.marklogic.client.document.ServerTransform;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.query.StructuredQueryBuilder;
import com.marklogic.client.query.StructuredQueryDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class AthleteDb {

    private static Logger logger = LoggerFactory.getLogger(AthleteDb.class);
    private DatabaseClient client, stagingCient;
    static Properties props;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        try {
            props = new Properties();
            props.load(AthleteDb.class.getClassLoader().getResourceAsStream("application.properties"));
        } catch (IOException e) {
            logger.error("Couldn't load properties.");
            e.printStackTrace();
            System.exit(Status.ERROR);
        }
    }

    static DatabaseClient stagingClient() {
            return DatabaseClientFactory.newClient(props.getProperty("mlHost"),
                            Integer.parseInt(props.getProperty("mlRestPort")),
                            "Documents",
                            new DatabaseClientFactory.DigestAuthContext(
                                    props.getProperty("mlAdminUsername"), props.getProperty("mlAdminPassword")));
    }

    static DatabaseClient prodClient() {
            return DatabaseClientFactory.newClient(props.getProperty("mlHost"),
                    Integer.parseInt(props.getProperty("mlRestPort")), new DatabaseClientFactory.DigestAuthContext(
                            props.getProperty("mlAdminUsername"), props.getProperty("mlAdminPassword")));
    }

    public AthleteDb() {
        client = AthleteDb.prodClient();
        stagingCient = AthleteDb.stagingClient();
    }

    public void loadModel() {
        logger.info("Loading model.");
        InputStream modelStream = this.getClass().getResourceAsStream("/athlete-db.json");
        InputStreamHandle handle = new InputStreamHandle().with(modelStream);
        DocumentMetadataHandle metadata = new DocumentMetadataHandle().withCollections("http://marklogic.com/entity-services/models");
        client.newJSONDocumentManager().write("/athlete-db.json", metadata, handle);
        logger.info("Loading model....done.");
    }

    public void harmonize() throws InterruptedException {
        logger.info("Harmonizing...");
        StructuredQueryBuilder qb = new StructuredQueryBuilder();
        StructuredQueryDefinition qdef = qb.collection("players");
        DataMovementManager stageMgr = stagingCient.newDataMovementManager();
        ServerTransform harmonizer = new ServerTransform("football-harmonizer");

        ApplyTransformListener listener = new ApplyTransformListener().withTransform(harmonizer)
                .withApplyResult(ApplyTransformListener.ApplyResult.IGNORE).onSuccess( inPlaceBatch -> {
                    logger.debug("batch transform SUCCESS");
                }).onBatchFailure((inPlaceBatch, e) -> {
                    if (e.getMessage().contains("transform extension does not exist")) {
                        logger.info("Batch failure because data flow hasn't been configured.  Moving on.");
                    } else {
                        logger.error("FAILURE on batch.", e);
                    }
                });

        QueryBatcher queryBatcher =
                stageMgr.newQueryBatcher(qdef)
                .withBatchSize(1000)
                .withThreadCount(3).onUrisReady(listener);
        stageMgr.startJob(queryBatcher);
        queryBatcher.awaitCompletion(Long.MAX_VALUE, TimeUnit.DAYS);
        stageMgr.stopJob(queryBatcher);
        logger.info("Harmonizing...done.");
    }

    public static void main(String[] args) throws InterruptedException {
        AthleteDb adb = new AthleteDb();
        adb.loadModel();
        try {
            adb.harmonize();
        } catch (FailedRequestException e) {
            if (e.getMessage().contains("transform extension does not exist")) {
                logger.info("Looks like harmonize was run before project setup.  Moving on.");
            }
            else {
                throw (e);
            }
        }
    }

}
