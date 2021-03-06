package it.unibz.inf.ontop.dbschema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import it.unibz.inf.ontop.exception.MetadataExtractionException;
import it.unibz.inf.ontop.injection.OntopSQLCoreConfiguration;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class BasicViewWithConstraintsParsingTest {
    private static final String VIEW_FILE = "src/test/resources/person/basic_views_with_constraints.json";
    private static final String DBMETADATA_FILE = "src/test/resources/person/person_with_constraints.db-extract.json";

    ImmutableSet<OntopViewDefinition> viewDefinitions = loadViewDefinitions(VIEW_FILE, DBMETADATA_FILE);

    public BasicViewWithConstraintsParsingTest() throws Exception {
    }

    /**
     * Both the parent "id" and added "status" constraints are present in the views
     */
    @Test
    public void testPersonAddUniqueConstraint() throws Exception {
        ImmutableSet<String> constraints = viewDefinitions.stream()
                .map(RelationDefinition::getUniqueConstraints)
                .flatMap(Collection::stream)
                .map(UniqueConstraint::getAttributes)
                .flatMap(Collection::stream)
                .map(v -> v.getID().getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("status", "id"), constraints);
    }

    /**
     * The dependent of the FD is correctly added by a viewfile
     */
    @Test
    public void testPersonAddFunctionalDependencyDependent() throws Exception {
        ImmutableSet<String> otherFD = viewDefinitions.stream()
                .map(RelationDefinition::getOtherFunctionalDependencies)
                .flatMap(Collection::stream)
                .map(FunctionalDependency::getDependents)
                .flatMap(Collection::stream)
                .map(d -> d.getID().getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("country"), otherFD);
    }

    /**
     * The determinant of the FD is correctly added by a viewfile
     */
    @Test
    public void testPersonAddFunctionalDependencyDeterminant() throws Exception {
        ImmutableSet<String> otherFD = viewDefinitions.stream()
                .map(RelationDefinition::getOtherFunctionalDependencies)
                .flatMap(Collection::stream)
                .map(FunctionalDependency::getDeterminants)
                .flatMap(Collection::stream)
                .map(d -> d.getID().getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("locality"), otherFD);
    }

    /**
     * Add FK destination relation name via viewfile
     */
    @Test
    public void testPersonAddForeignKey_DestinationRelation() throws Exception {
        ImmutableSet<String> destination_relation = viewDefinitions.stream()
                .map(RelationDefinition::getForeignKeys)
                .flatMap(Collection::stream)
                .map(ForeignKeyConstraint::getReferencedRelation)
                .map(d -> d.getID().getComponents().get(0).getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("statuses"), destination_relation);
    }

    /**
     * Add destination relation foreign key column name via viewfile
     */
    @Test
    public void testPersonAddForeignKey_DestinationColumn() throws Exception {
        ImmutableSet<String> destination_column = viewDefinitions.stream()
                .map(RelationDefinition::getForeignKeys)
                .flatMap(Collection::stream)
                .map(ForeignKeyConstraint::getComponents)
                .map(c -> c.get(0).getReferencedAttribute().getID().getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("status_id"), destination_column);
    }

    /**
     * Add source relation key column name via viewfile
     */
    @Test
    public void testPersonAddForeignKey_SourceColumn() throws Exception {
        ImmutableSet<String> source_column = viewDefinitions.stream()
                .map(RelationDefinition::getForeignKeys)
                .flatMap(Collection::stream)
                .map(ForeignKeyConstraint::getComponents)
                .map(c -> c.get(0).getAttribute().getID().getName())
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("status"), source_column);
    }

    /**
     * Add new foreign key name
     */
    @Test
    public void testPersonAddForeignKey_FKName() throws Exception {
        ImmutableSet<String> fk_name = viewDefinitions.stream()
                .map(RelationDefinition::getForeignKeys)
                .flatMap(Collection::stream)
                .map(ForeignKeyConstraint::getName)
                .collect(ImmutableCollectors.toSet());

        assertEquals(ImmutableSet.of("status_id_fkey"), fk_name);
    }

    protected ImmutableSet<OntopViewDefinition> loadViewDefinitions(String viewFilePath,
                                                                    String dbMetadataFilePath)
            throws Exception {

        OntopSQLCoreConfiguration configuration = OntopSQLCoreConfiguration.defaultBuilder()
                .jdbcUrl("jdbc:h2:mem:nowhere")
                .jdbcDriver("org.h2.Driver")
                .build();

        Injector injector = configuration.getInjector();
        SerializedMetadataProvider.Factory serializedMetadataProviderFactory = injector.getInstance(SerializedMetadataProvider.Factory.class);
        OntopViewMetadataProvider.Factory viewMetadataProviderFactory = injector.getInstance(OntopViewMetadataProvider.Factory.class);

        SerializedMetadataProvider dbMetadataProvider;
        try (Reader dbMetadataReader = new FileReader(dbMetadataFilePath)) {
            dbMetadataProvider = serializedMetadataProviderFactory.getMetadataProvider(dbMetadataReader);
        }

        OntopViewMetadataProvider viewMetadataProvider;
        try (Reader viewReader = new FileReader(viewFilePath)) {
            viewMetadataProvider = viewMetadataProviderFactory.getMetadataProvider(dbMetadataProvider, viewReader);
        }

        ImmutableMetadata metadata = ImmutableMetadata.extractImmutableMetadata(viewMetadataProvider);

        return metadata.getAllRelations().stream()
                .filter(r -> r instanceof OntopViewDefinition)
                .map(r -> (OntopViewDefinition) r)
                .collect(ImmutableCollectors.toSet());
    }
}
