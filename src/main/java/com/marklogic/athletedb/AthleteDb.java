package com.marklogic.athletedb;

import ch.qos.logback.core.status.Status;
import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.datamovement.*;
import com.marklogic.client.document.ServerTransform;
import com.marklogic.client.io.DocumentMetadataHandle;
import com.marklogic.client.io.InputStreamHandle;
import com.marklogic.client.query.QueryDefinition;
import com.marklogic.client.query.QueryManager;
import com.marklogic.client.query.StructuredQueryBuilder;
import com.marklogic.client.semantics.SPARQLQueryDefinition;
import com.marklogic.client.semantics.SPARQLQueryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class AthleteDb {

    private static Logger logger = LoggerFactory.getLogger(CodeGenerator.class);
    private DatabaseClient client, stagingCient;

    static DatabaseClient stagingClient() {
        try {
            Properties props = new Properties();
            props.load(DatabaseClient.class.getClassLoader().getResourceAsStream("application.properties"));
            Path currentRelativePath = Paths.get("");
            String projectDir = currentRelativePath.toAbsolutePath().toString();

            DatabaseClient client =
                    DatabaseClientFactory.newClient(props.getProperty("mlHost"),
                            Integer.parseInt(props.getProperty("mlRestPort")),
                            "Documents",
                            new DatabaseClientFactory.DigestAuthContext(
                                    props.getProperty("mlAdminUsername"), props.getProperty("mlAdminPassword")));
            return client;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(Status.ERROR);
        }
        return null;
    }
    static DatabaseClient newClient() {
        try {
            Properties props = new Properties();
            props.load(DatabaseClient.class.getClassLoader().getResourceAsStream("application.properties"));
            Path currentRelativePath = Paths.get("");
            String projectDir = currentRelativePath.toAbsolutePath().toString();

            DatabaseClient client =
                    DatabaseClientFactory.newClient(props.getProperty("mlHost"),
                    Integer.parseInt(props.getProperty("mlRestPort")), new DatabaseClientFactory.DigestAuthContext(
                            props.getProperty("mlAdminUsername"), props.getProperty("mlAdminPassword")));
            return client;
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(Status.ERROR);
        }
        return null;
    }

    public AthleteDb() {
        client = AthleteDb.newClient();
        stagingCient = AthleteDb.stagingClient();
    }

    public void loadModel() {
        InputStream modelStream = this.getClass().getResourceAsStream("/athlete-db.json");
        InputStreamHandle handle = new InputStreamHandle().with(modelStream);
        DocumentMetadataHandle metadata = new DocumentMetadataHandle().withCollections("http://marklogic.com/entity-services/models");
        client.newJSONDocumentManager().write("/athlete-db.json", metadata, handle);
    }

    public void getPlayerIris() throws InterruptedException {
        StructuredQueryBuilder qb = new StructuredQueryBuilder();
        QueryDefinition qdef = qb.collection("players");
        DataMovementManager stageMgr = stagingCient.newDataMovementManager();
        ServerTransform harmonizer = new ServerTransform("football-harmonizer");
        ApplyTransformListener listener = new ApplyTransformListener().withTransform(harmonizer)
                .withApplyResult(ApplyTransformListener.ApplyResult.IGNORE).onSuccess((dbClient, inPlaceBatch) -> {
                    logger.debug("batch transform SUCCESS");
                }).onBatchFailure((dbClient, inPlaceBatch, throwable) -> {
                    logger.error("FAILURE on batch:" + inPlaceBatch.toString() + "\n", throwable);
                    //System.err.println(throwable.getMessage());
                    //System.err.print(String.join("\n", inPlaceBatch.getItems()) + "\n");
                });

        QueryBatcher queryBatcher =
                stageMgr.newQueryBatcher(qdef)
                .withBatchSize(1000)
                .withThreadCount(3).onUrisReady(listener);
        stageMgr.startJob(queryBatcher);
        queryBatcher.awaitCompletion(Long.MAX_VALUE, TimeUnit.DAYS);
        stageMgr.stopJob(queryBatcher);
    }

    public static void main(String[] args) throws InterruptedException {
        AthleteDb adb = new AthleteDb();
        adb.loadModel();
        adb.getPlayerIris();

    }

}
