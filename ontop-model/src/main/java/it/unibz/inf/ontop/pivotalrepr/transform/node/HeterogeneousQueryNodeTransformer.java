package it.unibz.inf.ontop.pivotalrepr.transform.node;

import it.unibz.inf.ontop.pivotalrepr.*;

/**
 * TODO: explain
 */
public interface HeterogeneousQueryNodeTransformer<P extends NodeTransformationProposal> {

    P transform(FilterNode filterNode);

    P transform(ExtensionalDataNode extensionalDataNode);

    P transform(LeftJoinNode leftJoinNode);

    P transform(UnionNode unionNode);

    P transform(IntensionalDataNode intensionalDataNode);

    P transform(InnerJoinNode innerJoinNode);

    P transform(ConstructionNode constructionNode);

    P transform(EmptyNode emptyNode);

    P transform(TrueNode trueNode);
}