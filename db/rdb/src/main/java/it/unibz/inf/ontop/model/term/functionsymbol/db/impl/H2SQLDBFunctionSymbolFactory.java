package it.unibz.inf.ontop.model.term.functionsymbol.db.impl;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBBooleanFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBFunctionSymbol;
import it.unibz.inf.ontop.model.type.DBTermType;
import it.unibz.inf.ontop.model.type.DBTypeFactory;
import it.unibz.inf.ontop.model.type.TypeFactory;

import java.util.function.Function;

public class H2SQLDBFunctionSymbolFactory extends AbstractSQLDBFunctionSymbolFactory {

    private static final String UUID_STR = "RANDOM_UUID";
    private static final String REGEXP_LIKE_STR = "REGEXP_LIKE";

    @Inject
    private H2SQLDBFunctionSymbolFactory(TypeFactory typeFactory) {
        super(createDefaultNormalizationTable(typeFactory),
                createH2RegularFunctionTable(typeFactory), typeFactory);
    }

    protected static ImmutableTable<String, Integer, DBFunctionSymbol> createH2RegularFunctionTable(
            TypeFactory typeFactory) {
        DBTypeFactory dbTypeFactory = typeFactory.getDBTypeFactory();
        DBTermType dbStringType = dbTypeFactory.getDBStringType();
        DBTermType dbBooleanType = dbTypeFactory.getDBBooleanType();
        DBTermType abstractRootDBType = dbTypeFactory.getAbstractRootDBType();

        Table<String, Integer, DBFunctionSymbol> table = HashBasedTable.create(
                createDefaultRegularFunctionTable(typeFactory));
        DBFunctionSymbol uiidFunctionSymbol = new DefaultSQLSimpleTypedDBFunctionSymbol(UUID_STR, 0, dbStringType,
                false, abstractRootDBType);
        table.put(UUID_STR, 0, uiidFunctionSymbol);

        DBBooleanFunctionSymbol regexpLike2 = new DefaultSQLSimpleDBBooleanFunctionSymbol(REGEXP_LIKE_STR, 2, dbBooleanType,
                abstractRootDBType);
        table.put(REGEXP_LIKE_STR, 2, regexpLike2);

        DBBooleanFunctionSymbol regexpLike3 = new DefaultSQLSimpleDBBooleanFunctionSymbol(REGEXP_LIKE_STR, 3, dbBooleanType,
                abstractRootDBType);
        table.put(REGEXP_LIKE_STR, 3, regexpLike3);

        return ImmutableTable.copyOf(table);
    }

    @Override
    protected String serializeContains(ImmutableList<? extends ImmutableTerm> terms,
                                       Function<ImmutableTerm, String> termConverter,
                                       TermFactory termFactory) {
        return String.format("(POSITION(%s,%s) > 0)",
                termConverter.apply(terms.get(1)),
                termConverter.apply(terms.get(0)));
    }

    @Override
    public DBFunctionSymbol getDBUUIDFunctionSymbol() {
        return getRegularDBFunctionSymbol(UUID_STR, 0);
    }

    @Override
    public DBBooleanFunctionSymbol getDBRegexpMatches2() {
        return (DBBooleanFunctionSymbol) getRegularDBFunctionSymbol(REGEXP_LIKE_STR, 2);
    }

    @Override
    public DBBooleanFunctionSymbol getDBRegexpMatches3() {
        return (DBBooleanFunctionSymbol) getRegularDBFunctionSymbol(REGEXP_LIKE_STR, 3);
    }
}