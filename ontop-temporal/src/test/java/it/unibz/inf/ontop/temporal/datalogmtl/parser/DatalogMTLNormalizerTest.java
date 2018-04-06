package it.unibz.inf.ontop.temporal.datalogmtl.parser;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import it.unibz.inf.ontop.injection.OntopMappingConfiguration;
import it.unibz.inf.ontop.injection.SpecificationFactory;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.spec.datalogmtl.parser.DatalogMTLSyntaxParser;
import it.unibz.inf.ontop.spec.datalogmtl.parser.impl.DatalogMTLNormalizerImpl;
import it.unibz.inf.ontop.spec.datalogmtl.parser.impl.DatalogMTLSyntaxParserImpl;
import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import it.unibz.inf.ontop.temporal.model.DatalogMTLFactory;
import it.unibz.inf.ontop.temporal.model.DatalogMTLProgram;
import it.unibz.inf.ontop.temporal.model.impl.DatalogMTLFactoryImpl;
import junit.framework.TestCase;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import static it.unibz.inf.ontop.spec.impl.TestingTools.ATOM_FACTORY;
import static it.unibz.inf.ontop.spec.impl.TestingTools.TERM_FACTORY;

public class DatalogMTLNormalizerTest {
    private final static Logger log = LoggerFactory.getLogger(DatalogMTLParserTest.class);
    private final SpecificationFactory specificationFactory;
    private final AtomFactory atomFactory;

    public DatalogMTLNormalizerTest() {
        OntopMappingConfiguration defaultConfiguration = OntopMappingConfiguration.defaultBuilder()
                .enableTestMode()
                .build();
        Injector injector = defaultConfiguration.getInjector();
        this.specificationFactory = injector.getInstance(SpecificationFactory.class);
        this.atomFactory = injector.getInstance(AtomFactory.class);
    }

    @Test
    public void test() {
        final boolean result = parse(readFile("src/test/resources/rule.dmtl"));
        TestCase.assertTrue(result);
    }

    private boolean parse(String input) {
        DatalogMTLFactory datalogMTLFactory = new DatalogMTLFactoryImpl();
        DatalogMTLSyntaxParser parser = new DatalogMTLSyntaxParserImpl(ATOM_FACTORY,
                TERM_FACTORY);

        DatalogMTLNormalizerImpl normalizer = new DatalogMTLNormalizerImpl(datalogMTLFactory, atomFactory);
        DatalogMTLProgram program;
        try {
            program = parser.parse(input);
            //program = normalizer.normalize(program);
            log.debug("mapping " + program);
        } catch (Exception e) {
            log.debug(e.getMessage());
            return false;
        }
        return true;
    }

    private PrefixManager getPrefixManager() {
        return specificationFactory.createPrefixManager(ImmutableMap.of(
                PrefixManager.DEFAULT_PREFIX, "http://obda.inf.unibz.it/testcase#",
                "ex:", "http://www.example.org/"
        ));
    }

    private String readFile(String path){
        String output = "";
        try {
            BufferedReader bufferedReader =
                    new BufferedReader(new FileReader(path));

            String newLine;
            while ((newLine = bufferedReader.readLine()) != null){
                output += newLine + "\n";
            }
            return output.trim();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}