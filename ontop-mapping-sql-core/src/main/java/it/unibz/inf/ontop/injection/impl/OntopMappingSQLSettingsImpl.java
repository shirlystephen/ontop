package it.unibz.inf.ontop.injection.impl;

import it.unibz.inf.ontop.injection.OntopMappingSQLSettings;
import it.unibz.inf.ontop.injection.OntopSQLSettings;

import java.util.Properties;


public class OntopMappingSQLSettingsImpl extends OntopMappingSettingsImpl implements OntopMappingSQLSettings {

    private static final String DEFAULT_PROPERTY_FILE = "mapping-sql-default.properties";
    private final OntopSQLSettings sqlSettings;

    OntopMappingSQLSettingsImpl(Properties properties) {
        super(loadProperties(properties));
        sqlSettings = new OntopSQLSettingsImpl(copyProperties());
    }

    private static Properties loadProperties(Properties userProperties) {
        Properties properties = OntopSQLSettingsImpl.loadDefaultSQLProperties();
        properties.putAll(loadDefaultMappingSQLProperties());
        properties.putAll(userProperties);
        return properties;
    }

    public static Properties loadDefaultMappingSQLProperties() {
        return loadDefaultPropertiesFromFile(OntopMappingSQLSettings.class, DEFAULT_PROPERTY_FILE);
    }

    @Override
    public String getJdbcUrl() {
        return sqlSettings.getJdbcUrl();
    }

    @Override
    public String getJdbcName() {
        return sqlSettings.getJdbcName();
    }

    @Override
    public String getJdbcUser() {
        return sqlSettings.getJdbcUser();
    }

    public String getJdbcPassword() {
        return sqlSettings.getJdbcPassword();
    }

    @Override
    public String getJdbcDriver() {
        return sqlSettings.getJdbcDriver();
    }
}
