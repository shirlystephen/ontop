package it.unibz.inf.ontop.model.type.impl;

import it.unibz.inf.ontop.model.term.functionsymbol.BNodePredicate;
import it.unibz.inf.ontop.model.term.functionsymbol.DatatypePredicate;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.model.term.TermConstants;
import it.unibz.inf.ontop.model.term.functionsymbol.URITemplatePredicate;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.type.TermType;
import it.unibz.inf.ontop.exception.IncompatibleTermException;
import it.unibz.inf.ontop.model.vocabulary.RDF;

import java.util.Optional;

import static it.unibz.inf.ontop.model.OntopModelSingletons.TYPE_FACTORY;

public class TermTypeInferenceTools {

    private static final Optional<TermType> OPTIONAL_OBJECT_TERM_TYPE = Optional.of(TYPE_FACTORY.getIRITermType());
    private static final Optional<TermType> OPTIONAL_BNODE_TERM_TYPE = Optional.of(TYPE_FACTORY.getBlankNodeType());
    private static final Optional<TermType> OPTIONAL_UNBOUND_TERM_TYPE = Optional.of(TYPE_FACTORY.getUnboundTermType());

    private static final DatatypePredicate LITERAL_LANG_PREDICATE = TYPE_FACTORY.getRequiredTypePredicate(RDF.LANGSTRING);

    /**
     * TODO: simplify this method
     */
    public static Optional<TermType> inferType(ImmutableTerm term) throws IncompatibleTermException {
        if(term instanceof ImmutableFunctionalTerm){
            ImmutableFunctionalTerm f = (ImmutableFunctionalTerm) term;
            Predicate typePred = f.getFunctionSymbol();

            /*
             * TODO: generalize this
             */
            if(f instanceof ImmutableExpression) {
                return ((ImmutableExpression) f).getOptionalTermType();
            }
            else if (typePred instanceof DatatypePredicate){
                /*
                 * Special case: langString
                 */
                if (typePred.equals(LITERAL_LANG_PREDICATE)) {
                    if (f.getTerms().size() != 2) {
                        throw new IllegalStateException("A lang literal function should have two arguments");
                    }
                    ImmutableTerm secondArgument = f.getArguments().get(1);
                    if (!(secondArgument instanceof Constant))
                        // TODO: return a proper exception (internal bug)
                        throw new IllegalStateException("A lang literal function must have a constant language tag");
                    return Optional.of(TYPE_FACTORY.getLangTermType(((Constant)secondArgument).getValue()));
                }
                return TYPE_FACTORY.getInternalType((DatatypePredicate) typePred);

            } else if (typePred instanceof URITemplatePredicate) {
                return  OPTIONAL_OBJECT_TERM_TYPE;
            } else if (typePred instanceof BNodePredicate){
                return OPTIONAL_BNODE_TERM_TYPE;
            }
            else {
                throw new IllegalArgumentException("Unexpected functional term: " + term);
            }
        }
        else if(term instanceof Variable){
            return Optional.empty();
        } else if(term instanceof ValueConstant){
            /**
             * Deals with the ugly definition of the NULL constant.
             * COL_TYPE of NULL should be NULL!
             */
            if (term == TermConstants.NULL) {
                return OPTIONAL_UNBOUND_TERM_TYPE;
            }
            else {
                return Optional.of(((ValueConstant) term).getType());
            }
        } else if(term instanceof URIConstant){
            return OPTIONAL_OBJECT_TERM_TYPE;
        }
        else {
            throw new IllegalStateException("Unexpected term: " + term);
        }
    }
}