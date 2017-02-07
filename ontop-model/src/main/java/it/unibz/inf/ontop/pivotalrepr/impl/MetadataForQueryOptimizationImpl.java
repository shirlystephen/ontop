package it.unibz.inf.ontop.pivotalrepr.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import it.unibz.inf.ontop.model.DBMetadata;
import it.unibz.inf.ontop.model.UriTemplateMatcher;
import it.unibz.inf.ontop.pivotalrepr.MetadataForQueryOptimization;
import it.unibz.inf.ontop.model.AtomPredicate;

public class MetadataForQueryOptimizationImpl implements MetadataForQueryOptimization {

    private final ImmutableMultimap<AtomPredicate, ImmutableList<Integer>> primaryKeys;
    private final UriTemplateMatcher uriTemplateMatcher;
    private final DBMetadata dbMetadata;

    public MetadataForQueryOptimizationImpl(
            DBMetadata dbMetadata,
            ImmutableMultimap<AtomPredicate, ImmutableList<Integer>> primaryKeys, UriTemplateMatcher uriTemplateMatcher) {
        this.primaryKeys = primaryKeys;
        this.uriTemplateMatcher = uriTemplateMatcher;
        this.dbMetadata = dbMetadata;
    }

    @Override
    public ImmutableMultimap<AtomPredicate, ImmutableList<Integer>> getUniqueConstraints() {
        return primaryKeys;
    }

    @Override
    public UriTemplateMatcher getUriTemplateMatcher() {
        return uriTemplateMatcher;
    }

    @Override
    public DBMetadata getDBMetadata() {
        return dbMetadata;
    }
}