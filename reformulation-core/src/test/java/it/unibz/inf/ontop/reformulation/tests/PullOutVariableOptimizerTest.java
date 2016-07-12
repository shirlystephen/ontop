package it.unibz.inf.ontop.reformulation.tests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import it.unibz.inf.ontop.model.*;
import it.unibz.inf.ontop.model.impl.AtomPredicateImpl;
import it.unibz.inf.ontop.model.impl.ImmutabilityTools;
import it.unibz.inf.ontop.model.impl.OBDADataFactoryImpl;
import it.unibz.inf.ontop.owlrefplatform.core.optimization.PullOutVariableOptimizer;
import it.unibz.inf.ontop.pivotalrepr.*;
import it.unibz.inf.ontop.pivotalrepr.equivalence.IQSyntacticEquivalenceChecker;
import it.unibz.inf.ontop.pivotalrepr.impl.*;
import it.unibz.inf.ontop.pivotalrepr.impl.tree.DefaultIntermediateQueryBuilder;
import org.junit.Test;

import java.util.Optional;

import static it.unibz.inf.ontop.model.ExpressionOperation.EQ;
import static junit.framework.TestCase.assertTrue;

public class PullOutVariableOptimizerTest {

    private final static AtomPredicate TABLE1_PREDICATE = new AtomPredicateImpl("table1", 2);
    private final static AtomPredicate TABLE2_PREDICATE = new AtomPredicateImpl("table2", 2);
    private final static AtomPredicate TABLE3_PREDICATE = new AtomPredicateImpl("table3", 2);
    private final static AtomPredicate TABLE4_PREDICATE = new AtomPredicateImpl("table2", 3);
    private final static AtomPredicate TABLE5_PREDICATE = new AtomPredicateImpl("table3", 2);
    private final static AtomPredicate ANS1_PREDICATE1 = new AtomPredicateImpl("ans1", 4);
    private final static AtomPredicate ANS1_PREDICATE2 = new AtomPredicateImpl("ans1", 3);
    private final static AtomPredicate ANS1_PREDICATE3 = new AtomPredicateImpl("ans1", 2);

    private final static OBDADataFactory DATA_FACTORY = OBDADataFactoryImpl.getInstance();
    private final static Variable X = DATA_FACTORY.getVariable("X");
    private final static Variable X0 = DATA_FACTORY.getVariable("Xf0");
    private final static Variable X1 = DATA_FACTORY.getVariable("Xf1");
    private final static Variable X2 = DATA_FACTORY.getVariable("Xf2");
    private final static Variable X3 = DATA_FACTORY.getVariable("Xf3");
    private final static Variable X4 = DATA_FACTORY.getVariable("Xf0f3");
    private final static Variable X5 = DATA_FACTORY.getVariable("Xf0f1");
    private final static Variable Y = DATA_FACTORY.getVariable("Y");
    private final static Variable Y1 = DATA_FACTORY.getVariable("Yf1");
    private final static Variable Y2 = DATA_FACTORY.getVariable("Yf2");
    private final static Variable Z = DATA_FACTORY.getVariable("Z");
    private final static Variable W = DATA_FACTORY.getVariable("W");

    private final static ImmutableExpression EXPRESSION1 = DATA_FACTORY.getImmutableExpression(
            EQ, X, X0);
    private final static ImmutableExpression EXPRESSION2 = DATA_FACTORY.getImmutableExpression(
            EQ, Y, Y1);
    private final static ImmutableExpression EXPRESSION3 = DATA_FACTORY.getImmutableExpression(
            EQ, X, X1);
    private final static ImmutableExpression EXPRESSION4 = DATA_FACTORY.getImmutableExpression(
            EQ, X, X2);
    private final static ImmutableExpression EXPRESSION5 = DATA_FACTORY.getImmutableExpression(
            EQ, X, X3);
    private final static ImmutableExpression EXPRESSION6 = DATA_FACTORY.getImmutableExpression(
            EQ, Y, Y2);
    private final static ImmutableExpression EXPRESSION7 = DATA_FACTORY.getImmutableExpression(
            EQ, X0, X4);
    private final static ImmutableExpression EXPRESSION8 = DATA_FACTORY.getImmutableExpression(
            EQ, X0, X5);


    private final MetadataForQueryOptimization metadata;

    public PullOutVariableOptimizerTest() {
        this.metadata = initMetadata();
    }

    private static MetadataForQueryOptimization initMetadata() {
        ImmutableMultimap.Builder<AtomPredicate, ImmutableList<Integer>> uniqueKeyBuilder = ImmutableMultimap.builder();
        return new MetadataForQueryOptimizationImpl(uniqueKeyBuilder.build(), new UriTemplateMatcher());
    }

    @Test
    public void testJoiningConditionTest1() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder1 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode = new ConstructionNodeImpl(projectionAtom.getVariables());

        InnerJoinNode joinNode1 = new InnerJoinNodeImpl(Optional.empty());
        ExtensionalDataNode dataNode1 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, Y));
        ExtensionalDataNode dataNode2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X, Z));

        queryBuilder1.init(projectionAtom, constructionNode);
        queryBuilder1.addChild(constructionNode, joinNode1);
        queryBuilder1.addChild(joinNode1, dataNode1);
        queryBuilder1.addChild(joinNode1, dataNode2);

        IntermediateQuery query1 = queryBuilder1.build();

        System.out.println("\nBefore optimization: \n" +  query1);

        PullOutVariableOptimizer pullOutVariableOptimizer = new PullOutVariableOptimizer();
        IntermediateQuery optimizedQuery = pullOutVariableOptimizer.optimize(query1);

        System.out.println("\nAfter optimization: \n" +  optimizedQuery);

        IntermediateQueryBuilder queryBuilder2 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom2 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectionAtom.getVariables());

        InnerJoinNode joinNode2 = new InnerJoinNodeImpl(Optional.of(EXPRESSION1));
        ExtensionalDataNode dataNode3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X0, Z));

        queryBuilder2.init(projectionAtom2, constructionNode2);
        queryBuilder2.addChild(constructionNode2, joinNode2);
        queryBuilder2.addChild(joinNode2, dataNode1);
        queryBuilder2.addChild(joinNode2, dataNode3);

        IntermediateQuery query2 = queryBuilder2.build();

        System.out.println("\nExpected: \n" +  query2);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(optimizedQuery, query2));
    }

    @Test
    public void testJoiningConditionTest2() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder1 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode = new ConstructionNodeImpl(projectionAtom.getVariables());

        LeftJoinNode leftJoinNode1 = new LeftJoinNodeImpl(Optional.empty());
        ExtensionalDataNode dataNode1 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, Y));
        ExtensionalDataNode dataNode2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, X, Y, Z));

        queryBuilder1.init(projectionAtom, constructionNode);
        queryBuilder1.addChild(constructionNode, leftJoinNode1);
        queryBuilder1.addChild(leftJoinNode1, dataNode1, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        queryBuilder1.addChild(leftJoinNode1, dataNode2, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query1 = queryBuilder1.build();

        System.out.println("\nBefore optimization: \n" +  query1);

        PullOutVariableOptimizer pullOutVariableOptimizer = new PullOutVariableOptimizer();
        IntermediateQuery optimizedQuery = pullOutVariableOptimizer.optimize(query1);

        System.out.println("\nAfter optimization: \n" +  optimizedQuery);

        IntermediateQueryBuilder queryBuilder2 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom2 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectionAtom.getVariables());

        LeftJoinNode leftJoinNode2 = new LeftJoinNodeImpl(ImmutabilityTools.foldBooleanExpressions(EXPRESSION1, EXPRESSION2));
        ExtensionalDataNode dataNode3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, X0, Y1, Z));

        queryBuilder2.init(projectionAtom2, constructionNode2);
        queryBuilder2.addChild(constructionNode2, leftJoinNode2);
        queryBuilder2.addChild(leftJoinNode2, dataNode1, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        queryBuilder2.addChild(leftJoinNode2, dataNode3, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query2 = queryBuilder2.build();

        System.out.println("\nExpected: \n" +  query2);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(optimizedQuery, query2));
    }

    @Test
    public void testJoiningConditionTest3() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder1 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom1 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE3, X, Y);
        ConstructionNode constructionNode1 = new ConstructionNodeImpl(projectionAtom1.getVariables());

        LeftJoinNode leftJoinNode1 = new LeftJoinNodeImpl(Optional.empty());
        ExtensionalDataNode dataNode1 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, X, Y));
        ExtensionalDataNode dataNode2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, X, Y, X));

        queryBuilder1.init(projectionAtom1, constructionNode1);
        queryBuilder1.addChild(constructionNode1, leftJoinNode1);
        queryBuilder1.addChild(leftJoinNode1, dataNode1, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        queryBuilder1.addChild(leftJoinNode1, dataNode2, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query1 = queryBuilder1.build();

        System.out.println("\nBefore optimization: \n" +  query1);

        PullOutVariableOptimizer pullOutVariableOptimizer = new PullOutVariableOptimizer();
        IntermediateQuery optimizedQuery = pullOutVariableOptimizer.optimize(query1);

        System.out.println("\nAfter optimization: \n" +  optimizedQuery);


        IntermediateQueryBuilder expectedQuery = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom2 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE3, X, Y);
        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectionAtom1.getVariables());

        LeftJoinNode leftJoinNode2 = new LeftJoinNodeImpl(ImmutabilityTools.foldBooleanExpressions(EXPRESSION1, EXPRESSION2, EXPRESSION7));
        FilterNode filterNode1 = new FilterNodeImpl(EXPRESSION4);
        ExtensionalDataNode dataNode3 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, X2, Y));
        ExtensionalDataNode dataNode4 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE4_PREDICATE, X0, Y1, X4));

        expectedQuery.init(projectionAtom2, constructionNode2);
        expectedQuery.addChild(constructionNode2, leftJoinNode2);
        expectedQuery.addChild(leftJoinNode2, filterNode1, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        expectedQuery.addChild(filterNode1, dataNode3);
        expectedQuery.addChild(leftJoinNode2, dataNode4, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query2 = expectedQuery.build();

        System.out.println("\nExpected: \n" +  query2);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(optimizedQuery, query2));
    }

    @Test
    public void testJoiningConditionTest4() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder1 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE1, X, Y, Z, W);
        ConstructionNode constructionNode = new ConstructionNodeImpl(projectionAtom.getVariables());

        InnerJoinNode joinNode1 = new InnerJoinNodeImpl(Optional.empty());
        LeftJoinNode leftJoinNode1 = new LeftJoinNodeImpl(Optional.empty());
        ExtensionalDataNode dataNode1 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, Y));
        ExtensionalDataNode dataNode2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X, Z));
        ExtensionalDataNode dataNode3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE3_PREDICATE, X, W));

        queryBuilder1.init(projectionAtom, constructionNode);
        queryBuilder1.addChild(constructionNode, joinNode1);
        queryBuilder1.addChild(joinNode1, dataNode1);
        queryBuilder1.addChild(joinNode1, leftJoinNode1);
        queryBuilder1.addChild(leftJoinNode1, dataNode2, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        queryBuilder1.addChild(leftJoinNode1, dataNode3, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query1 = queryBuilder1.build();

        System.out.println("\nBefore optimization: \n" +  query1);

        PullOutVariableOptimizer pullOutVariableOptimizer = new PullOutVariableOptimizer();
        IntermediateQuery optimizedQuery = pullOutVariableOptimizer.optimize(query1);

        System.out.println("\nAfter optimization: \n" +  optimizedQuery);

        IntermediateQueryBuilder expectedQueryBuilder = new DefaultIntermediateQueryBuilder(metadata);

        InnerJoinNode joinNode2 = new InnerJoinNodeImpl(Optional.of(EXPRESSION1));
        LeftJoinNode leftJoinNode2 = new LeftJoinNodeImpl(Optional.of(EXPRESSION8));
        ExtensionalDataNode dataNode4 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, Y));
        ExtensionalDataNode dataNode5 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X0, Z));
        ExtensionalDataNode dataNode6 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE3_PREDICATE, X5, W));

        expectedQueryBuilder.init(projectionAtom, constructionNode);
        expectedQueryBuilder.addChild(constructionNode, joinNode2);
        expectedQueryBuilder.addChild(joinNode2, dataNode4);
        expectedQueryBuilder.addChild(joinNode2, leftJoinNode2);
        expectedQueryBuilder.addChild(leftJoinNode2, dataNode5, NonCommutativeOperatorNode.ArgumentPosition.LEFT);
        expectedQueryBuilder.addChild(leftJoinNode2, dataNode6, NonCommutativeOperatorNode.ArgumentPosition.RIGHT);

        IntermediateQuery query2 = expectedQueryBuilder.build();

        System.out.println("\nExpected: \n" +  query2);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(optimizedQuery, query2));
    }

    @Test
    public void testJoiningConditionTest5() throws EmptyQueryException {

        IntermediateQueryBuilder queryBuilder1 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode = new ConstructionNodeImpl(projectionAtom.getVariables());

        InnerJoinNode joinNode1 = new InnerJoinNodeImpl(Optional.empty());
        ExtensionalDataNode dataNode1 =  new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE1_PREDICATE, X, Y));
        ExtensionalDataNode dataNode2 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X, Z, Y));

        queryBuilder1.init(projectionAtom, constructionNode);
        queryBuilder1.addChild(constructionNode, joinNode1);
        queryBuilder1.addChild(joinNode1, dataNode1);
        queryBuilder1.addChild(joinNode1, dataNode2);

        IntermediateQuery query1 = queryBuilder1.build();

        System.out.println("\nBefore optimization: \n" +  query1);

        PullOutVariableOptimizer pullOutVariableOptimizer = new PullOutVariableOptimizer();
        IntermediateQuery optimizedQuery = pullOutVariableOptimizer.optimize(query1);

        System.out.println("\nAfter optimization: \n" +  optimizedQuery);

        IntermediateQueryBuilder queryBuilder2 = new DefaultIntermediateQueryBuilder(metadata);
        DistinctVariableOnlyDataAtom projectionAtom2 = DATA_FACTORY.getDistinctVariableOnlyDataAtom(ANS1_PREDICATE2, X, Y, Z);
        ConstructionNode constructionNode2 = new ConstructionNodeImpl(projectionAtom.getVariables());

        InnerJoinNode joinNode2 = new InnerJoinNodeImpl(ImmutabilityTools.foldBooleanExpressions(EXPRESSION1, EXPRESSION2));
        ExtensionalDataNode dataNode3 = new ExtensionalDataNodeImpl(DATA_FACTORY.getDataAtom(TABLE2_PREDICATE, X0, Z, Y1));

        queryBuilder2.init(projectionAtom2, constructionNode2);
        queryBuilder2.addChild(constructionNode2, joinNode2);
        queryBuilder2.addChild(joinNode2, dataNode1);
        queryBuilder2.addChild(joinNode2, dataNode3);

        IntermediateQuery query2 = queryBuilder2.build();

        System.out.println("\nExpected: \n" +  query2);

        assertTrue(IQSyntacticEquivalenceChecker.areEquivalent(optimizedQuery, query2));
    }

}