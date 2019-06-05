package it.unibz.inf.ontop.answering.reformulation.input.translation.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import it.unibz.inf.ontop.answering.reformulation.input.translation.RDF4JInputQueryTranslator;
import it.unibz.inf.ontop.exception.OntopInternalBugException;
import it.unibz.inf.ontop.exception.OntopInvalidInputQueryException;
import it.unibz.inf.ontop.exception.OntopUnsupportedInputQueryException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.FunctionSymbolFactory;
import it.unibz.inf.ontop.model.term.functionsymbol.LangSPARQLFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.RDFTermFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.SPARQLFunctionSymbol;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.TermTypeInference;
import it.unibz.inf.ontop.model.type.TypeFactory;
import it.unibz.inf.ontop.model.vocabulary.SPARQL;
import it.unibz.inf.ontop.model.vocabulary.XPathFunction;
import it.unibz.inf.ontop.model.vocabulary.XSD;
import it.unibz.inf.ontop.substitution.ImmutableSubstitution;
import it.unibz.inf.ontop.substitution.InjectiveVar2VarSubstitution;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.CoreUtilsFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.R2RMLIRISafeEncoder;
import it.unibz.inf.ontop.utils.VariableGenerator;
import org.apache.commons.rdf.api.RDF;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.*;
import org.eclipse.rdf4j.query.parser.ParsedQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class RDF4JInputQueryTranslatorImpl implements RDF4JInputQueryTranslator {

    private final CoreUtilsFactory coreUtilsFactory;
    private final TermFactory termFactory;
    private final SubstitutionFactory substitutionFactory;
    private final TypeFactory typeFactory;
    private final IntermediateQueryFactory iqFactory;
    private final AtomFactory atomFactory;
    private final RDF rdfFactory;
    private final FunctionSymbolFactory functionSymbolFactory;

    @Inject
    public RDF4JInputQueryTranslatorImpl(CoreUtilsFactory coreUtilsFactory, TermFactory termFactory, SubstitutionFactory substitutionFactory,
                                         TypeFactory typeFactory, IntermediateQueryFactory iqFactory, AtomFactory atomFactory, RDF rdfFactory,
                                         FunctionSymbolFactory functionSymbolFactory) {
        this.coreUtilsFactory = coreUtilsFactory;
        this.termFactory = termFactory;
        this.substitutionFactory = substitutionFactory;
        this.typeFactory = typeFactory;
        this.iqFactory = iqFactory;
        this.atomFactory = atomFactory;
        this.rdfFactory = rdfFactory;
        this.functionSymbolFactory = functionSymbolFactory;
    }

    @Override
    public IQ translate(ParsedQuery pq) {
        VariableGenerator variableGenerator = coreUtilsFactory.createVariableGenerator(ImmutableList.of());

        // Assumption: the binding names in the parsed query are in the desired order
        ImmutableList<Variable> projectedVars = pq.getTupleExpr().getBindingNames().stream()
                .map(termFactory::getVariable)
                .collect(ImmutableCollectors.toList());
        IQTree tree = null;
        try {
            tree = translate(pq.getTupleExpr(), variableGenerator).iqTree;
        } catch (OntopInvalidInputQueryException | OntopUnsupportedInputQueryException e) {
            e.printStackTrace();
        }
        if (tree.getVariables().containsAll(projectedVars)) {
            return iqFactory.createIQ(
                    atomFactory.getDistinctVariableOnlyDataAtom(
                            atomFactory.getRDFAnswerPredicate(projectedVars.size()),
                            projectedVars
                    ),
                    tree
            );
        }
        throw new Sparql2IqConversionException("The IQ obtained after converting the SPARQL query does not project al expected variables");
    }

    private TranslationResult translate(TupleExpr node, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        if (node instanceof StatementPattern) // triple pattern
            return translateTriplePattern((StatementPattern) node);

        if (node instanceof Join)    // JOIN algebra operation
            return translateJoin((Join) node, variableGenerator);

        if (node instanceof LeftJoin)   // OPTIONAL algebra operation
            return translateOptional((LeftJoin) node, variableGenerator);

        if (node instanceof Union)    // UNION algebra operation
            return translateUnion((Union) node, variableGenerator);

        if (node instanceof Filter)    // FILTER algebra operation
            return translateFilter((Filter) node, variableGenerator);

        if (node instanceof Projection)   // PROJECT algebra operation
            return translateProjection((Projection) node, variableGenerator);

        if (node instanceof Slice)
            return translateSlice((Slice) node, variableGenerator);

        if (node instanceof Distinct)
            return translateDistinctOrReduced(node, variableGenerator);

        if (node instanceof Reduced)
            return translateDistinctOrReduced(node, variableGenerator);

        if (node instanceof SingletonSet)
            return translateSingletonSet();

        if (node instanceof Extension)
            return translateExtension((Extension) node, variableGenerator);

        if (node instanceof BindingSetAssignment)
            return translateBindingSetAssignment((BindingSetAssignment) node);

        throw new Sparql2IqConversionException("Unexpected SPARQL operator : " + node.toString());
    }


    private TranslationResult translateBindingSetAssignment(BindingSetAssignment node) {

        ImmutableSet<Variable> valueVars = node.getBindingNames().stream()
                .map(termFactory::getVariable)
                .collect(ImmutableCollectors.toSet());
        ImmutableSet<Variable> valueAssuredVars = node.getAssuredBindingNames().stream()
                .map(termFactory::getVariable)
                .collect(ImmutableCollectors.toSet());

        return new TranslationResult(
                iqFactory.createNaryIQTree(
                        iqFactory.createUnionNode(valueVars),
                        getBindingSetCns(
                                node,
                                valueVars
                        )),
                Sets.difference(valueVars, valueAssuredVars).immutableCopy()
        );
    }

    private ImmutableList<IQTree> getBindingSetCns(BindingSetAssignment node, ImmutableSet<Variable> valueVars) {
        return StreamSupport.stream(node.getBindingSets().spliterator(), false)
                .map(b -> getBindingSetCn(b, node.getBindingNames(), valueVars))
                .map(n -> iqFactory.createUnaryIQTree(
                        n,
                        iqFactory.createTrueNode()
                ))
                .collect(ImmutableCollectors.toList());
    }

    private ConstructionNode getBindingSetCn(BindingSet bindingSet, Set<String> bindingNames, ImmutableSet<Variable> valueVars) {
        return iqFactory.createConstructionNode(
                valueVars,
                substitutionFactory.getSubstitution(
                        bindingNames.stream()
                                .collect(ImmutableCollectors.toMap(
                                        termFactory::getVariable,
                                        x -> getTermForBinding(
                                                x,
                                                bindingSet
                                        )))));
    }

    private ImmutableTerm getTermForBinding(String x, BindingSet bindingSet) {
        Binding binding = bindingSet.getBinding(x);
        return binding == null
                ? termFactory.getNullConstant()
                : getTermForLiteralOrIri(binding.getValue());
    }

    private TranslationResult translateSingletonSet() {
        return new TranslationResult(
                iqFactory.createTrueNode(),
                ImmutableSet.of()
        );
    }

    private TranslationResult translateDistinctOrReduced(TupleExpr genNode, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {
        TranslationResult child;
        if (genNode instanceof Distinct) {
            child = translate(((Distinct) genNode).getArg(), variableGenerator);
        } else if (genNode instanceof Reduced) {
            child = translate(((Reduced) genNode).getArg(), variableGenerator);
        } else {
            throw new Sparql2IqConversionException("Unexpected node type for node: " + genNode.toString());
        }
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createDistinctNode(),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }

    private TranslationResult translateSlice(Slice node, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {
        TranslationResult child = translate(node.getArg(), variableGenerator);
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createSliceNode(node.getOffset(), node.getLimit()),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }

    private TranslationResult translateFilter(Filter filter, VariableGenerator variableGenerator)
            throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        TranslationResult child = translate(filter.getArg(), variableGenerator);
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createFilterNode(
                                getFilterExpression(
                                        filter.getCondition(),
                                        child.iqTree.getVariables()
                                )),
                        child.iqTree
                ),
                child.nullableVariables
        );
    }


    private TranslationResult translateOptional(LeftJoin leftJoin, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        TranslationResult leftTranslation = translate(leftJoin.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(leftJoin.getRightArg(), variableGenerator);

        IQTree leftQuery = leftTranslation.iqTree;
        IQTree rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> projectedFromRight = rightTranslation.iqTree.getVariables();
        ImmutableSet<Variable> projectedFromLeft = leftTranslation.iqTree.getVariables();

        ImmutableSet<Variable> toCoalesce = Sets.intersection(nullableFromLeft, projectedFromRight).immutableCopy();

        ImmutableSet<Variable> toRenameRight = Sets.union(
                toCoalesce,
                Sets.intersection(
                        nullableFromRight,
                        projectedFromLeft
                ).immutableCopy()
        ).immutableCopy();

        ImmutableSet<Variable> bothSidesNullable = Sets.intersection(nullableFromLeft, nullableFromRight).immutableCopy();

        InjectiveVar2VarSubstitution leftRenamingSubstitution = generateVariabledSubstitution(toCoalesce,
                variableGenerator);
        InjectiveVar2VarSubstitution rightRenamingSubstitution = generateVariabledSubstitution(toRenameRight,
                variableGenerator);
        ImmutableSubstitution<ImmutableTerm> topSubstitution = substitutionFactory.getSubstitution(toCoalesce.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getImmutableFunctionalTerm(
                                functionSymbolFactory.getSPARQLFunctionSymbol(SPARQL.COALESCE, 2).get(),
                                leftRenamingSubstitution.get(x),
                                rightRenamingSubstitution.get(x)
                        ))));

        LeftJoinNode ljNode;
        ImmutableSet<Variable> newSetOfNullableVars;

        Optional<ImmutableExpression> filterExpression;
        ValueExpr filterCondition = leftJoin.getCondition();
        if (filterCondition != null) {
            ImmutableSet<Variable> knownVariables =
                    Sets.union(leftQuery.getKnownVariables(), rightQuery.getKnownVariables()).immutableCopy();
            ImmutableExpression filterExpressionBeforeSubst = getFilterExpression(filterCondition, knownVariables);
            filterExpression = Optional.of(topSubstitution.applyToBooleanExpression(filterExpressionBeforeSubst));
        } else {
            filterExpression = Optional.empty();
        }

        Optional<ImmutableExpression> joinCondition = generateJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSidesNullable,
                filterExpression
        );

        ljNode = iqFactory.createLeftJoinNode(joinCondition);

        ImmutableSet<Variable> newNullableVars =
                Sets.difference(rightQuery.getVariables(), leftQuery.getVariables())
                        .immutableCopy();

        newSetOfNullableVars =
                Sets.union(Sets.union(nullableFromLeft, nullableFromRight), newNullableVars)
                        .immutableCopy();

        IQTree joinQuery = buildJoinQuery(
                ljNode,
                leftQuery,
                rightQuery,
                leftRenamingSubstitution, rightRenamingSubstitution, topSubstitution
        );

        return new TranslationResult(joinQuery, newSetOfNullableVars);
    }

    private InjectiveVar2VarSubstitution generateVariabledSubstitution(
            ImmutableSet<Variable> nullableVariables, VariableGenerator variableGenerator) {

        return substitutionFactory.getInjectiveVar2VarSubstitution(nullableVariables.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        variableGenerator::generateNewVariableFromVar
                )));
    }

    private TranslationResult translateJoin(Join join, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        TranslationResult leftTranslation = translate(join.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(join.getRightArg(), variableGenerator);

        IQTree leftQuery = leftTranslation.iqTree;
        IQTree rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> toSubstituteLeft =
                Sets.intersection(nullableFromLeft, rightQuery.getVariables()).immutableCopy();
        ImmutableSet<Variable> toSubstituteRight =
                Sets.intersection(nullableFromRight, leftQuery.getVariables()).immutableCopy();

        InjectiveVar2VarSubstitution leftRenamingSubstitution = generateVariabledSubstitution(toSubstituteLeft,
                variableGenerator);
        InjectiveVar2VarSubstitution rightRenamingSubstitution = generateVariabledSubstitution(toSubstituteRight,
                variableGenerator);

        ImmutableSet<Variable> bothSideNullableVars = Sets.intersection(nullableFromLeft, nullableFromRight).immutableCopy();

        ImmutableSubstitution<ImmutableTerm> topSubstitution = substitutionFactory.getSubstitution(bothSideNullableVars.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getImmutableFunctionalTerm(
                                functionSymbolFactory.getSPARQLFunctionSymbol(SPARQL.COALESCE,2).get(),
                                leftRenamingSubstitution.get(x),
                                rightRenamingSubstitution.get(x)
                        ))));

        InnerJoinNode joinNode;
        ImmutableSet<Variable> newSetOfNullableVars;

        joinNode = iqFactory.createInnerJoinNode(generateJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSideNullableVars,
                Optional.empty())
        );

        newSetOfNullableVars = Sets.union(nullableFromLeft, nullableFromRight).immutableCopy();

        IQTree joinQuery = buildJoinQuery(
                joinNode,
                leftQuery,
                rightQuery,
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                topSubstitution
        );

        return new TranslationResult(joinQuery, newSetOfNullableVars);
    }

    private Optional<ImmutableExpression> generateJoinCondition(InjectiveVar2VarSubstitution leftRenamingSubstitution,
                                                                InjectiveVar2VarSubstitution rightRenamingSubstitution,
                                                                ImmutableSet<Variable> bothSideNullableVars,
                                                                Optional<ImmutableExpression> filterCondition) {

        Optional<ImmutableExpression> compatibilityCondition = generateCompatibleJoinCondition(
                leftRenamingSubstitution,
                rightRenamingSubstitution,
                bothSideNullableVars
        );
        return compatibilityCondition.isPresent() ?
                compatibilityCondition
                        .map(compatExpr -> filterCondition.map(
                                filterExpr -> termFactory.getConjunction(
                                        filterExpr,
                                        compatExpr
                                )).orElse(compatExpr)
                        ) :
                filterCondition;
    }

    private Optional<ImmutableExpression> generateCompatibleJoinCondition(
            InjectiveVar2VarSubstitution leftChildSubstitution,
            InjectiveVar2VarSubstitution rightChildSubstitution, ImmutableSet<Variable> bothSideNullableVars) {

        Stream<Variable> nullableVariableStream = Stream.concat(
                leftChildSubstitution.getDomain().stream(),
                rightChildSubstitution.getDomain().stream()
        ).distinct();

        return termFactory.getConjunction(nullableVariableStream
                .map(v -> generateCompatibleExpression(
                        v,
                        leftChildSubstitution,
                        rightChildSubstitution,
                        bothSideNullableVars
                )));
    }


    private ImmutableExpression generateCompatibleExpression(Variable outputVariable,
                                                             InjectiveVar2VarSubstitution leftChildSubstitution,
                                                             InjectiveVar2VarSubstitution rightChildSubstitution,
                                                             ImmutableSet<Variable> bothSideNullableVars) {
        ImmutableExpression isNullExpression;

        Variable leftVariable = leftChildSubstitution.applyToVariable(outputVariable);
        Variable rightVariable = rightChildSubstitution.applyToVariable(outputVariable);

        ImmutableExpression equalityCondition =
                termFactory.getStrictEquality(leftVariable, rightVariable);

        if (bothSideNullableVars.contains(outputVariable)) {
            ImmutableExpression leftIsNull = termFactory.getDBIsNull(leftVariable);
            ImmutableExpression rightIsNull = termFactory.getDBIsNull(rightVariable);
            isNullExpression = termFactory.getDisjunction(leftIsNull, rightIsNull);
        } else if (leftChildSubstitution.isDefining(outputVariable)) {
            isNullExpression = termFactory.getDBIsNull(leftVariable);
        } else {
            isNullExpression = termFactory.getDBIsNull(rightVariable);
        }
        return termFactory.getDisjunction(equalityCondition, isNullExpression);
    }

    private IQTree buildJoinQuery(JoinLikeNode joinNode,
                                  IQTree leftQuery,
                                  IQTree rightQuery,
                                  InjectiveVar2VarSubstitution leftRenamingSubstitution,
                                  InjectiveVar2VarSubstitution rightRenamingSubstitution,
                                  ImmutableSubstitution<ImmutableTerm> topSubstitution
    ) {

        IQTree leftTree = iqFactory.createUnaryIQTree(
                getJoinOperandCN(
                        leftQuery,
                        leftRenamingSubstitution
                ),
                leftQuery
        );
        IQTree rightTree = iqFactory.createUnaryIQTree(
                getJoinOperandCN(
                        rightQuery,
                        rightRenamingSubstitution
                ),
                rightQuery
        );

        ImmutableSet<Variable> projectedVariables =
                Sets.union(leftTree.getVariables(), rightTree.getVariables())
                        .immutableCopy();

        return iqFactory.createUnaryIQTree(
                iqFactory.createConstructionNode(
                        projectedVariables,
                        topSubstitution
                ),
                getJoinTree(
                        joinNode,
                        leftTree,
                        rightTree
                ));
    }

    private ConstructionNode getJoinOperandCN(IQTree tree, InjectiveVar2VarSubstitution sub) {
        return iqFactory.createConstructionNode(
                tree.getVariables().stream()
                        .map(sub::applyToVariable)
                        .collect(ImmutableCollectors.toSet()),
                (ImmutableSubstitution) sub
        );
    }

    private IQTree getJoinTree(JoinLikeNode joinNode, IQTree leftTree, IQTree rightTree) {
        if (joinNode instanceof LeftJoinNode) {
            return iqFactory.createBinaryNonCommutativeIQTree(
                    (LeftJoinNode) joinNode,
                    leftTree,
                    rightTree
            );
        }
        if (joinNode instanceof InnerJoinNode) {
            return iqFactory.createNaryIQTree(
                    (InnerJoinNode) joinNode,
                    ImmutableList.of(
                            leftTree,
                            rightTree
                    )
            );
        }
        throw new Sparql2IqConversionException("Left or inner join expected");
    }

    private TranslationResult translateProjection(Projection node, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {
        TranslationResult child = translate(node.getArg(), variableGenerator);
        IQTree subQuery = child.iqTree;

        List<ProjectionElem> projectionElems = node.getProjectionElemList().getElements();
        ImmutableSubstitution<ImmutableTerm> topSubstitution =
                substitutionFactory.getSubstitution(projectionElems.stream()
                        .filter(pe -> !pe.getTargetName().equals(pe.getSourceName()))
                        .collect(ImmutableCollectors.toMap(
                                pe -> termFactory.getVariable(pe.getTargetName()),
                                pe -> termFactory.getVariable(pe.getSourceName())
                        )));

        ImmutableList<Variable> projectedVariables = projectionElems.stream()
                .map(pe -> termFactory.getVariable(pe.getTargetName()))
                .collect(ImmutableCollectors.toList());
        ConstructionNode projectNode = iqFactory.createConstructionNode(
                ImmutableSet.copyOf(projectedVariables),
                topSubstitution
        );
        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                       projectNode,
                       subQuery
                ),
                child.nullableVariables.stream()
                        .map(topSubstitution::applyToVariable)
                        .filter(t -> t instanceof Variable)
                        .map(t -> (Variable) t)
                        .collect(ImmutableCollectors.toSet())
        );
    }

    private TranslationResult translateUnion(Union union, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {
        TranslationResult leftTranslation = translate(union.getLeftArg(), variableGenerator);
        TranslationResult rightTranslation = translate(union.getRightArg(), variableGenerator);

        IQTree leftQuery = leftTranslation.iqTree;
        IQTree rightQuery = rightTranslation.iqTree;

        ImmutableSet<Variable> nullableFromLeft = leftTranslation.nullableVariables;
        ImmutableSet<Variable> nullableFromRight = rightTranslation.nullableVariables;

        ImmutableSet<Variable> leftVariables = leftQuery.getVariables();
        ImmutableSet<Variable> rightVariables = rightQuery.getVariables();

        ImmutableSet<Variable> nullOnLeft = Sets.difference(rightVariables, leftVariables).immutableCopy();
        ImmutableSet<Variable> nullOnRight = Sets.difference(leftVariables, rightVariables).immutableCopy();

        ImmutableSet<Variable> allNullable = Sets.union(nullableFromLeft, Sets.union(nullableFromRight, Sets.union(nullOnLeft, nullOnRight))).immutableCopy();

        ImmutableSet<Variable> rootVariables = Sets.union(leftVariables, rightVariables).immutableCopy();

        ImmutableSubstitution<ImmutableTerm> leftSubstitution = substitutionFactory.getSubstitution(nullOnLeft.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getNullConstant()
                )));

        ImmutableSubstitution<ImmutableTerm> rightSubstitution = substitutionFactory.getSubstitution(nullOnRight.stream()
                .collect(ImmutableCollectors.toMap(
                        x -> x,
                        x -> termFactory.getNullConstant()
                )));

        ConstructionNode leftCn = iqFactory.createConstructionNode(rootVariables, leftSubstitution);
        ConstructionNode rightCn = iqFactory.createConstructionNode(rootVariables, rightSubstitution);

        UnionNode unionNode = iqFactory.createUnionNode(rootVariables);

        ConstructionNode rootNode = iqFactory.createConstructionNode(rootVariables);

        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        rootNode,
                        iqFactory.createNaryIQTree(
                                unionNode,
                                ImmutableList.of(
                                        iqFactory.createUnaryIQTree(
                                                leftCn,
                                                leftQuery
                                        ),
                                        iqFactory.createUnaryIQTree(
                                                rightCn,
                                                rightQuery
                                        )))),
                allNullable
        );
    }

    private TranslationResult translateTriplePattern(StatementPattern triple) {

        return new TranslationResult(
                iqFactory.createIntensionalDataNode(
                        atomFactory.getIntensionalTripleAtom(
                                translateVar(triple.getSubjectVar()),
                                translateVar(triple.getPredicateVar()),
                                translateVar(triple.getObjectVar())
                        )),
                ImmutableSet.of()
        );
    }

    private TranslationResult translateExtension(Extension node, VariableGenerator variableGenerator) throws OntopInvalidInputQueryException, OntopUnsupportedInputQueryException {

        TranslationResult childTranslation = translate(node.getArg(), variableGenerator);
        IQTree childQuery = childTranslation.iqTree;
        ImmutableSet<Variable> childNullableVars = childTranslation.nullableVariables;

        ImmutableSubstitution<ImmutableTerm> extSubstitution = substitutionFactory.getSubstitution(
                node.getElements().stream()
                        .filter(ee -> !(ee.getExpr() instanceof Var && ee.getName().equals(((Var) ee.getExpr()).getName())))
                        .collect(ImmutableCollectors.toMap(
                                x -> termFactory.getVariable(x.getName()),
                                x -> getExpression(
                                        x.getExpr(),
                                        childQuery.getVariables())
                        )));

        ImmutableSet<Variable> nullableVars = Stream.concat(
                childNullableVars.stream(),
                extSubstitution.getImmutableMap().entrySet().stream()
                        .filter(e -> e.getValue().getVariableStream()
                                .anyMatch(childNullableVars::contains))
                .map(Map.Entry::getKey)
        ).collect(ImmutableCollectors.toSet());

        ImmutableSet<Variable> projectedVariables = Stream.concat(
                childQuery.getVariables().stream(),
                extSubstitution.getDomain().stream()
        ).collect(ImmutableCollectors.toSet());

        return new TranslationResult(
                iqFactory.createUnaryIQTree(
                        iqFactory.createConstructionNode(
                                projectedVariables,
                                extSubstitution
                        ),
                        childQuery
                ),
                nullableVars
        );
    }

    private ImmutableTerm getTermForLiteralOrIri(Value v)  {

        if (v instanceof Literal) {
            try {
                return getTermForLiteral((Literal) v);
            } catch (OntopUnsupportedInputQueryException e) {
                throw new RuntimeException(e);
            }
        }
        if (v instanceof IRI)
            return getTermForIri((IRI) v);

        throw new RuntimeException(new OntopUnsupportedInputQueryException("The value " + v + " is not supported yet!"));
    }

    private ImmutableTerm getTermForLiteral(Literal literal) throws OntopUnsupportedInputQueryException {
        IRI typeURI = literal.getDatatype();
        String value = literal.getLabel();
        Optional<String> lang = literal.getLanguage();

        if (lang.isPresent()) {
            return termFactory.getRDFLiteralConstant(value, lang.get());

        } else {
            RDFDatatype type;
            /*
             * default data type is xsd:string
             */
            if (typeURI == null) {
                type = typeFactory.getXsdStringDatatype();
            } else {
                type = typeFactory.getDatatype(rdfFactory.createIRI(typeURI.stringValue()));
            }

            if (type == null)
                // ROMAN (27 June 2016): type1 in open-eq-05 test would not be supported in OWL
                // the actual value is LOST here
                return termFactory.getConstantIRI(rdfFactory.createIRI(typeURI.stringValue()));
            // old strict version:
            // throw new RuntimeException("Unsupported datatype: " + typeURI);

            // BC-march-19: it seems that SPARQL does not forbid invalid lexical forms
            //     (e.g. when interpreted as an EBV, they evaluate to false)
            // However, it is unclear in which cases it would be interesting to offer a (partial) robustness to
            // such errors coming from the input query
            // check if the value is (lexically) correct for the specified datatype
            if (!XMLDatatypeUtil.isValidValue(value, typeURI))
                throw new OntopUnsupportedInputQueryException(
                        String.format("Invalid lexical forms are not accepted. Found for %s: %s", type.toString(), value));

            return termFactory.getRDFLiteralConstant(value, type);

        }
    }

    /**
     * @param expr      expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     */

    private ImmutableExpression getFilterExpression(ValueExpr expr, ImmutableSet<Variable> variables) {

        ImmutableTerm term = getExpression(expr, variables);

        ImmutableTerm xsdBooleanTerm = term.inferType()
                .flatMap(TermTypeInference::getTermType)
                .filter(t -> t instanceof RDFDatatype)
                .filter(t -> ((RDFDatatype) t).isA(XSD.BOOLEAN))
                .isPresent()
                ? term
                : termFactory.getSPARQLEffectiveBooleanValue(term);

        return termFactory.getRDF2DBBooleanFunctionalTerm(xsdBooleanTerm);
    }

    /**
     *
     * @param expr expression
     * @param variables the set of variables that can occur in the expression
     *                  (the rest will be replaced with NULL)
     * @return term
     */

    private ImmutableTerm getExpression(ValueExpr expr, Set<Variable> variables) {

        // PrimaryExpression ::= BrackettedExpression | BuiltInCall | iriOrFunction |
        //                          RDFLiteral | NumericLiteral | BooleanLiteral | Var
        // iriOrFunction ::= iri ArgList?

        if (expr instanceof Var) {
            Var v = (Var) expr;
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var) ? var : termFactory.getNullConstant();
        }
        else if (expr instanceof ValueConstant) {
            Value v = ((ValueConstant) expr).getValue();
            return getTermForLiteralOrIri(v);
        }
        else if (expr instanceof Bound) {
            // BOUND (Sec 17.4.1.1)
            // xsd:boolean  BOUND (variable var)
            Var v = ((Bound) expr).getArg();
            Variable var = termFactory.getVariable(v.getName());
            return variables.contains(var) ?
                    termFactory.getImmutableFunctionalTerm(
                            functionSymbolFactory.getRequiredSPARQLFunctionSymbol(
                                    SPARQL.BOUND,
                                    1
                            ),
                            var
                    ) :
                    termFactory.getRDFLiteralConstant("false", XSD.BOOLEAN);
        }
        else if (expr instanceof UnaryValueOperator) {
            ImmutableTerm term = getExpression(((UnaryValueOperator) expr).getArg(), variables);

            if (expr instanceof Not) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                        convertToXsdBooleanTerm(term));
            }
            else if (expr instanceof IsNumeric) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_NUMERIC, 1),
                        term);
            }
            else if (expr instanceof IsLiteral) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_LITERAL, 1),
                        term);
            }
            else if (expr instanceof IsURI) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_IRI, 1),
                        term);
            }
            else if (expr instanceof Str) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.STR, 1),
                        term);
            }
            else if (expr instanceof Datatype) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.DATATYPE, 1),
                        term);
            }
            else if (expr instanceof IsBNode) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.IS_BLANK, 1),
                        term);
            }
            else if (expr instanceof Lang) {
                ValueExpr arg = ((UnaryValueOperator) expr).getArg();
                if (arg instanceof Var)
                    return termFactory.getImmutableFunctionalTerm(
                            functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG, 1),
                            term);
                throw new RuntimeException(new OntopUnsupportedInputQueryException("A variable or a value is expected in " + expr));
            }
            // other subclasses
            // IRIFunction: IRI (Sec 17.4.2.8) for constructing IRIs
            // IsNumeric:  isNumeric (Sec 17.4.2.4) for checking whether the argument is a numeric value
            // AggregateOperatorBase: Avg, Min, Max, etc.
            // Like:  ??
            // IsResource: ??
            // LocalName: ??
            // Namespace: ??
            // Label: ??
        }
        else if (expr instanceof BinaryValueOperator) {
            BinaryValueOperator bexpr = (BinaryValueOperator) expr;
            ImmutableTerm term1 = getExpression(bexpr.getLeftArg(), variables);
            ImmutableTerm term2 = getExpression(bexpr.getRightArg(), variables);

            if (expr instanceof And) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_AND, 2),
                        convertToXsdBooleanTerm(term1), convertToXsdBooleanTerm(term2));
            }
            else if (expr instanceof Or) {
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LOGICAL_OR, 2),
                        convertToXsdBooleanTerm(term1), convertToXsdBooleanTerm(term2));
            }
            else if (expr instanceof SameTerm) {
                // sameTerm (Sec 17.4.1.8)
                // Corresponds to the STRICT equality (same lexical value, same type)
                return termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.SAME_TERM, 2),
                        term1, term2);
            }
            else if (expr instanceof Regex) {
                // REGEX (Sec 17.4.3.14)
                // xsd:boolean  REGEX (string literal text, simple literal pattern)
                // xsd:boolean  REGEX (string literal text, simple literal pattern, simple literal flags)
                Regex reg = (Regex) expr;
                return (reg.getFlagsArg() != null)
                        ? termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 3),
                        term1, term2,
                        getExpression(reg.getFlagsArg(), variables))
                        : termFactory.getImmutableFunctionalTerm(
                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.REGEX, 2),
                        term1, term2);
            }
            else if (expr instanceof Compare) {
                // TODO: make it a SPARQLFunctionSymbol
                final SPARQLFunctionSymbol p;

                switch (((Compare) expr).getOperator()) {
                    case NE:
                        return termFactory.getImmutableFunctionalTerm(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getImmutableFunctionalTerm(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2),
                                        term1, term2));
                    case EQ:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.EQ, 2);
                        break;
                    case LT:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LESS_THAN, 2);
                        break;
                    case LE:
                        return termFactory.getImmutableFunctionalTerm(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getImmutableFunctionalTerm(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.GREATER_THAN, 2),
                                        term1, term2));
                    case GE:
                        return termFactory.getImmutableFunctionalTerm(
                                functionSymbolFactory.getRequiredSPARQLFunctionSymbol(XPathFunction.NOT.getIRIString(), 1),
                                termFactory.getImmutableFunctionalTerm(
                                        functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LESS_THAN, 2),
                                        term1, term2));
                    case GT:
                        p = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.GREATER_THAN, 2);
                        break;
                    default:
                        throw new RuntimeException(new OntopUnsupportedInputQueryException("Unsupported operator: " + expr));
                }
                return termFactory.getImmutableFunctionalTerm(p, term1, term2);
            }
            else if (expr instanceof MathExpr) {
                SPARQLFunctionSymbol f = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(
                        NumericalOperations.get(((MathExpr)expr).getOperator()), 2);
                return termFactory.getImmutableFunctionalTerm(f, term1, term2);
            }
            /*
             * Restriction: the first argument must be LANG(...) and the second  a constant
             * (for guaranteeing that the langMatches logic is not delegated to the native query)
             */
            else if (expr instanceof LangMatches) {
                if ((!((term1 instanceof Function)
                        && ((Function) term1).getFunctionSymbol() instanceof LangSPARQLFunctionSymbol))
                        || (!((term2 instanceof Function)
                        // TODO: support "real" constants (not wrapped into a functional term)
                        && ((Function) term2).getFunctionSymbol() instanceof RDFTermFunctionSymbol)) ) {
                    throw new RuntimeException(new OntopUnsupportedInputQueryException("The function langMatches is " +
                            "only supported with lang(..) function for the first argument and a constant for the second")
                        );
                }

                SPARQLFunctionSymbol langMatchesFunctionSymbol = functionSymbolFactory.getRequiredSPARQLFunctionSymbol(SPARQL.LANG_MATCHES, 2);

                return termFactory.getImmutableFunctionalTerm(langMatchesFunctionSymbol, term1, term2);
            }
        }
        else if (expr instanceof FunctionCall) {
            FunctionCall f = (FunctionCall) expr;

            ImmutableList<ImmutableTerm> terms = f.getArgs().stream()
                    .map(a -> getExpression(a,variables))
                    .collect(ImmutableCollectors.toList());

            Optional<SPARQLFunctionSymbol> optionalFunctionSymbol = functionSymbolFactory.getSPARQLFunctionSymbol(
                    f.getURI(), terms.size());

            if (optionalFunctionSymbol.isPresent()) {
                return termFactory.getImmutableFunctionalTerm(optionalFunctionSymbol.get(), terms);
            }
        }
        // other subclasses
        // SubQueryValueOperator
        // If
        // BNodeGenerator
        // NAryValueOperator (ListMemberOperator and Coalesce)
        throw new RuntimeException(new OntopUnsupportedInputQueryException("The expression " + expr + " is not supported yet!"));
    }



    /**
     * translates a RDF4J var, which can be a variable or a constant, into a Ontop term.
     *
     * @param var RDF4J var, which can be a variable or a constant
     */
    private VariableOrGroundTerm translateVar(Var var) {
        return (var.hasValue())?
                ImmutabilityTools.convertIntoVariableOrGroundTerm(
                        getTermForLiteralOrIri(var.getValue())
                ):
                getVariable(var);
    }

    private Variable getVariable(Var v) {
        return termFactory.getVariable(v.getName());
    }


    /**
     *
     * @param v URI object
     * @return term (URI template)
     */

    private ImmutableTerm getTermForIri(IRI v) {

        // Guohui(07 Feb, 2018): this logic should probably be moved to a different place, since some percentage-encoded
        // string of an IRI might be a part of an IRI template, but not from database value.
        String uri = R2RMLIRISafeEncoder.decode(v.stringValue());
        //String uri = v.stringValue();
        return termFactory.getConstantIRI(rdfFactory.createIRI(uri));
    }


    private ImmutableTerm convertToXsdBooleanTerm(ImmutableTerm term) {

        return term.inferType()
                .flatMap(TermTypeInference::getTermType)
                .filter(t -> t instanceof RDFDatatype)
                .filter(t -> ((RDFDatatype) t).isA(XSD.BOOLEAN))
                .isPresent()?
                term :
                termFactory.getSPARQLEffectiveBooleanValue(term);
    }

//    private void uncheckAndThrow(OntopUnsupportedInputQueryException e){
//        throw new RuntimeException(e);
//    }


    private static final ImmutableMap<MathExpr.MathOp, String> NumericalOperations =
            new ImmutableMap.Builder<MathExpr.MathOp, String>()
                    .put(MathExpr.MathOp.PLUS, SPARQL.NUMERIC_ADD)
                    .put(MathExpr.MathOp.MINUS, SPARQL.NUMERIC_SUBSTRACT)
                    .put(MathExpr.MathOp.MULTIPLY, SPARQL.NUMERIC_MULTIPLY)
                    .put(MathExpr.MathOp.DIVIDE, SPARQL.NUMERIC_DIVIDE)
                    .build();

    private static class TranslationResult {
        final IQTree iqTree;
        final ImmutableSet<Variable> nullableVariables;

        TranslationResult(IQTree iqTree, ImmutableSet<Variable> nullableVariables) {
            this.nullableVariables = nullableVariables;
            this.iqTree = iqTree;
        }
    }

    private static class Sparql2IqConversionException extends OntopInternalBugException {

        Sparql2IqConversionException(String s) {
            super(s);
        }
    }
}
