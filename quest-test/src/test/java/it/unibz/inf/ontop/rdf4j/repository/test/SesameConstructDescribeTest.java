package it.unibz.inf.ontop.rdf4j.repository.test;

/*
 * #%L
 * ontop-test
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;

import it.unibz.inf.ontop.rdf4j.repository.OntopRepositoryConnection;
import junit.framework.TestCase;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import it.unibz.inf.ontop.rdf4j.repository.OntopClassicInMemoryRepository;

/**
 * This unit test is to ensure the correctness of construct and describe
 * queries in ontop through the Sesame API. All tests should be green.
 * @author timi
 *
 */
public class SesameConstructDescribeTest{

	OntopRepositoryConnection con = null;
	Repository repo = null;
	ValueFactory fac = null;
	String fileName = "src/test/resources/describeConstruct.ttl";
	String owlFile = "src/test/resources/describeConstruct.owl";
	
	@Before
	public void setUp() throws Exception {
		
		try {
			System.out.println("In-memory quest repo.");
			
			repo = new OntopClassicInMemoryRepository("constructDescribe", owlFile, false, "TreeWitness");
			repo.initialize();
			con = (OntopRepositoryConnection) repo.getConnection();
			fac = con.getValueFactory();
			File data = new File(fileName);
			System.out.println(data.getAbsolutePath());
			if (data.canRead())
				con.add(data, "http://www.semanticweb.org/ontologies/test", RDFFormat.TURTLE, (Resource)null);
			else
				throw new Exception("The specified file cannot be found or has restricted access.");
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			throw e;
		}
	}
	
	@After
	public void tearDown() throws Exception {
		con.close();
		repo.shutDown();
	}
	
	@Test
	public void testInsertData() throws Exception {
		int result = 0;
		String queryString = "CONSTRUCT {?s ?p ?o} WHERE {?s ?p ?o}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			Statement s = gresult.next();
			result++;
			System.out.println(s.toString());
		}
		Assert.assertEquals(4, result);
	}
	@Test
	public void testDescribeUri0() throws Exception {
		boolean result = false;
		String queryString = "DESCRIBE <http://www.semanticweb.org/ontologies/test#p1>";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result = false;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertFalse(result);
	}
	
	@Test
	public void testDescribeUri1() throws Exception {
		int result = 0;
		String queryString = "DESCRIBE <http://example.org/D>";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(1, result);
	}
	
	@Test
	public void testDescribeUri2() throws Exception {
		int result = 0;
		String queryString = "DESCRIBE <http://example.org/C>";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(2, result);
	}
	
	@Test
	public void testDescribeVar0() throws Exception {
		boolean result = false;
		String queryString = "DESCRIBE ?x WHERE {<http://example.org/C> ?x ?y }";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result = false;
			Statement s = gresult.next();
			System.out.println(s.toString());
		}
		Assert.assertFalse(result);
	}
	
	@Test
	public void testDescribeVar1() throws Exception {
		int result = 0;
		String queryString = "DESCRIBE ?x WHERE {?x <http://www.semanticweb.org/ontologies/test#p2> <http://example.org/A>}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(1, result);
	}
	
	@Test
	public void testDescribeVar2() throws Exception {
		int result = 0;
		String queryString = "DESCRIBE ?x WHERE {?x <http://www.semanticweb.org/ontologies/test#p1> ?y}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(2, result);
	}
	
	@Test
	public void testConstruct0() throws Exception {
		boolean result = false;
		String queryString = "CONSTRUCT {?s ?p <http://www.semanticweb.org/ontologies/test/p1>} WHERE {?s ?p <http://www.semanticweb.org/ontologies/test/p1>}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result = false;
			Statement s = gresult.next();
			System.out.println(s.toString());
		}
		Assert.assertFalse(result);
	}
	
	@Test
	public void testConstruct1() throws Exception {
		int result = 0;
		String queryString = "CONSTRUCT { ?s ?p <http://example.org/D> } WHERE { ?s ?p <http://example.org/D>}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(1, result);
	}
	
	@Test
	public void testConstruct2() throws Exception {
		int result = 0;
		String queryString = "CONSTRUCT {<http://example.org/C> ?p ?o} WHERE {<http://example.org/C> ?p ?o}";
		GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
				queryString);

		GraphQueryResult gresult = graphQuery.evaluate();
		while (gresult.hasNext()) {
			result++;
			Statement s = gresult.next();
			//System.out.println(s.toString());
		}
		Assert.assertEquals(2, result);
	}

	// https://github.com/ontop/ontop/issues/161
    @Test
    public void testConstructOptional() throws Exception {
        int result = 0;
        String queryString = "PREFIX : <http://www.semanticweb.org/ontologies/test#> \n" +
                "CONSTRUCT { ?s :p ?o1. ?s :p ?o2. }\n" +
                "WHERE {\n" +
                "OPTIONAL {?s :p1 ?o1}\n" +
                "OPTIONAL {?s :p2 ?o2}\n" +
                "}";
        GraphQuery graphQuery = con.prepareGraphQuery(QueryLanguage.SPARQL,
                queryString);

        GraphQueryResult gresult = graphQuery.evaluate();
        while (gresult.hasNext()) {
            result++;
            Statement s = gresult.next();
            //System.out.println(s.toString());
        }
        Assert.assertEquals(2, result);
    }
	
	@Test
	public void testGetStatements0() throws Exception {
		boolean result = false;
		Resource subj = fac.createURI("http://www.semanticweb.org/ontologies/test/p1");
		RepositoryResult<Statement> results = con.getStatements(subj, null, null, false, (Resource)null);
		while (results.hasNext())
		{
			result = true;
			results.next();
		}
		Assert.assertFalse(result);
	}
	
	@Test
	public void testGetStatements1() throws Exception {
		int result = 0;
		Value obj = fac.createURI("http://example.org/D");
		RepositoryResult<Statement> results = con.getStatements(null, null, obj, false, (Resource)null);
		while (results.hasNext())
		{
			result++;
			results.next();
		}
		Assert.assertEquals(1, result);
	}
	
	@Test
	public void testGetStatements2() throws Exception {
		int result = 0;
		Resource subj = fac.createURI("http://example.org/C");
		RepositoryResult<Statement> results = con.getStatements(subj, null, null, false, (Resource)null);
		while (results.hasNext())
		{
			result++;
			results.next();
		}
		
		Assert.assertEquals(2, result);
	}

}
