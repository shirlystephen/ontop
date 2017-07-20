package it.unibz.inf.ontop.owlrefplatform.core;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import it.unibz.inf.ontop.answering.input.InputQueryFactory;
import it.unibz.inf.ontop.dbschema.DBMetadata;
import it.unibz.inf.ontop.exception.OntopConnectionException;
import it.unibz.inf.ontop.injection.OntopSystemSQLSettings;
import it.unibz.inf.ontop.answering.reformulation.IRIDictionary;
import it.unibz.inf.ontop.answering.reformulation.QueryTranslator;
import it.unibz.inf.ontop.sql.pool.JDBCConnectionPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.*;
import java.util.Optional;

/**
 * For RDBMS having a JDBC driver.
 */
public class JDBCConnector implements DBConnector {

    private final QueryTranslator queryReformulator;

    private final OntopSystemSQLSettings settings;
    private final Optional<IRIDictionary> iriDictionary;

    /* The active connection used for keeping in-memory DBs alive */
    private transient Connection localConnection;

    private final Logger log = LoggerFactory.getLogger(JDBCConnector.class);
    private final JDBCConnectionPool connectionPool;

    private final DBMetadata dbMetadata;
    private final InputQueryFactory inputQueryFactory;

    @AssistedInject
    private JDBCConnector(@Assisted QueryTranslator queryTranslator,
                          @Assisted DBMetadata dbMetadata,
                          @Nullable IRIDictionary iriDictionary,
                          JDBCConnectionPool connectionPool,
                          InputQueryFactory inputQueryFactory,
                          OntopSystemSQLSettings settings) {
        this.queryReformulator = queryTranslator;
        this.dbMetadata = dbMetadata;
        this.inputQueryFactory = inputQueryFactory;
        this.settings = settings;
        this.iriDictionary = Optional.ofNullable(iriDictionary);
        this.connectionPool = connectionPool;
    }

    /**
     * Keeps a permanent connection to the DB (if enabled in the settings).
     *
     * Needed by some in-memory DBs (such as H2).
     *
     */
    public boolean connect() throws OntopConnectionException {
        try {
            if (localConnection != null && !localConnection.isClosed()) {
                return true;
            }
            if (settings.isPermanentDBConnectionEnabled()) {
                localConnection = DriverManager.getConnection(settings.getJdbcUrl(),
                        settings.getJdbcUser(), settings.getJdbcPassword());
                return localConnection != null;
            }
        } catch (SQLException e) {
            throw new OntopConnectionException(e);
        }

        return true;
    }

    @Override
    public void close() {
        try {
            if (localConnection != null)
                localConnection.close();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        connectionPool.close();
    }

    public synchronized Connection getSQLPoolConnection() throws OntopConnectionException {
        try {
            return connectionPool.getConnection();
        } catch (SQLException e) {
            throw new OntopConnectionException(e);
        }
    }

    /***
     * Returns an OntopConnection, the main object that a client should use to
     * access the query answering services of Quest. With the QuestConnection
     * you can get a QuestStatement to execute queries.
     *
     * <p>
     * Note, the OntopConnection is not a normal JDBC connection. It is a
     * wrapper of one of the N JDBC connections that quest's connection pool
     * starts on initialization. Calling .close() will not actually close the
     * connection, with will just release it back to the pool.
     * <p>
     * to close all connections you must call DBConnector.close().
     *
     */
    @Override
    public OntopConnection getConnection() throws OntopConnectionException {

        return new QuestConnection(this, queryReformulator, getSQLPoolConnection(), iriDictionary,
                dbMetadata, inputQueryFactory, settings);
    }


}
