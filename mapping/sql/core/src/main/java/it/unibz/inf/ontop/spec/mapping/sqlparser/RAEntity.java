package it.unibz.inf.ontop.spec.mapping.sqlparser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.dbschema.QuotedID;
import it.unibz.inf.ontop.dbschema.RelationID;
import it.unibz.inf.ontop.model.term.ImmutableExpression;
import it.unibz.inf.ontop.spec.mapping.sqlparser.exception.IllegalJoinException;

import java.util.function.Function;

public interface RAEntity<T> {
    T withAlias(RelationID aliasId);

    T crossJoin(T right) throws IllegalJoinException;
    T naturalJoin(T right) throws IllegalJoinException;
    T joinUsing(T right, ImmutableSet<QuotedID> using) throws IllegalJoinException;
    T joinOn(T right, Function<RAExpressionAttributes, ImmutableList<ImmutableExpression>> getAtomOnExpression) throws IllegalJoinException;
}
