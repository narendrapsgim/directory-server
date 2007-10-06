/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server;


import org.apache.directory.server.core.partition.Partition;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.partition.impl.btree.Index;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.ldif.Entry;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.util.DateUtils;
import org.apache.directory.shared.ldap.util.NamespaceTools;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * A set of tests to make sure the negation operator is working 
 * properly when included in search filters. Created in response
 * to JIRA issue 
 * <a href="https://issues.apache.org/jira/browse/DIRSERVER-951">DIRSERVER-951</a>.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 519077 $
 */
public class NegationOperatorITest extends AbstractServerTest
{
    private LdapContext ctx = null;
    private List<Entry> loadedEntries;


    /**
     * Create context and entries for tests.
     */
    public void setUp() throws Exception
    {
        if ( this.getName().indexOf( "Indexed" ) != -1 )
        {
            JdbmPartition system = new JdbmPartition();
            system.setId( "system" );
            
            // @TODO need to make this configurable for the system partition
            system.setCacheSize( 500 );
            
            system.setSuffix( PartitionNexus.SYSTEM_PARTITION_SUFFIX );
    
            // Add indexed attributes for system partition
            Set<Index> indexedAttrs = new HashSet<Index>();
            indexedAttrs.add( new JdbmIndex( SchemaConstants.OBJECT_CLASS_AT ) );
            indexedAttrs.add( new JdbmIndex( SchemaConstants.OU_AT ) );
            system.setIndexedAttributes( indexedAttrs );
    
            // Add context entry for system partition
            Attributes systemEntry = new AttributesImpl();
            Attribute objectClassAttr = new AttributeImpl( SchemaConstants.OBJECT_CLASS_AT );
            objectClassAttr.add( SchemaConstants.TOP_OC );
            objectClassAttr.add( SchemaConstants.ORGANIZATIONAL_UNIT_OC );
            objectClassAttr.add( SchemaConstants.EXTENSIBLE_OBJECT_OC );
            systemEntry.put( objectClassAttr );
            systemEntry.put( SchemaConstants.CREATORS_NAME_AT, PartitionNexus.ADMIN_PRINCIPAL );
            systemEntry.put( SchemaConstants.CREATE_TIMESTAMP_AT, DateUtils.getGeneralizedTime() );
            systemEntry.put( NamespaceTools.getRdnAttribute( PartitionNexus.SYSTEM_PARTITION_SUFFIX ),
                NamespaceTools.getRdnValue( PartitionNexus.SYSTEM_PARTITION_SUFFIX ) );
            system.setContextEntry( systemEntry );
            
            apacheDS.getDirectoryService().setSystemPartition( system );
        }
        
        super.setUp();
        loadedEntries = super.loadTestLdif( true );
        ctx = getWiredContext();
        assertNotNull( ctx );
        assertEquals( 5, loadedEntries.size() );
    }


    /**
     * Closes context and destroys server.
     */
    public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;
        loadedEntries = null;
        super.tearDown();
    }
    

    /**
     * Tests to make sure a negated search for actors without ou
     * with value 'drama' returns those that do not have the attribute
     * and do not have a 'drama' value for ou if the attribute still
     * exists.  This test does not build an index on ou for the system
     * partition.
     */
    public void testSearchNotDrama() throws Exception
    {
        // jack black has ou but not drama, and joe newbie has no ou what so ever
        Set<SearchResult> results = getResults( "(!(ou=drama))" );
        assertTrue( contains( "uid=jblack,ou=actors,ou=system", results ) );
        assertTrue( contains( "uid=jnewbie,ou=actors,ou=system", results ) );
        assertEquals( 2, results.size() );
    }

    
    /**
     * Tests to make sure a negated search for actors without ou
     * with value 'drama' returns those that do not have the attribute
     * and do not have a 'drama' value for ou if the attribute still
     * exists.  This test DOES build an index on ou for the system
     * partition and should have failed if the bug in DIRSERVER-951
     * was present and reproducable.
     */
    public void testSearchNotDramaIndexed() throws Exception
    {
        // jack black has ou but not drama, and joe newbie has no ou what so ever
        Set<SearchResult> results = getResults( "(!(ou=drama))" );
        assertTrue( contains( "uid=jblack,ou=actors,ou=system", results ) );
        assertTrue( contains( "uid=jnewbie,ou=actors,ou=system", results ) );
        assertEquals( 2, results.size() );
    }

    
    /**
     * Tests to make sure a negated search for actors without ou
     * with value 'drama' returns those that do not have the attribute
     * and do not have a 'drama' value for ou if the attribute still
     * exists.  This test does not build an index on ou for the system
     * partition.
     */
    public void testSearchNotDramaNotNewbie() throws Exception
    {
        // jack black has ou but not drama, and joe newbie has no ou what so ever
        Set<SearchResult> results = getResults( "(& (!(uid=jnewbie)) (!(ou=drama)) )" );
        assertTrue( contains( "uid=jblack,ou=actors,ou=system", results ) );
        assertFalse( contains( "uid=jnewbie,ou=actors,ou=system", results ) );
        assertEquals( 1, results.size() );
    }

    
    /**
     * Tests to make sure a negated search for actors without ou
     * with value 'drama' returns those that do not have the attribute
     * and do not have a 'drama' value for ou if the attribute still
     * exists.  This test DOES build an index on ou for the system
     * partition and should have failed if the bug in DIRSERVER-951
     * was present and reproducable.
     */
    public void testSearchNotDramaNotNewbieIndexed() throws Exception
    {
        // jack black has ou but not drama, and joe newbie has no ou what so ever
        Set<SearchResult> results = getResults( "(& (!(uid=jnewbie)) (!(ou=drama)) )" );
        assertTrue( contains( "uid=jblack,ou=actors,ou=system", results ) );
        assertFalse( contains( "uid=jnewbie,ou=actors,ou=system", results ) );
        assertEquals( 1, results.size() );
    }

    
    boolean contains( String dn, Set<SearchResult> results )
    {
        for ( SearchResult result : results )
        {
            if ( result.getNameInNamespace().equals( dn ) )
            {
                return true;
            }
        }
        
        return false;
    }
    
    
    Set<SearchResult> getResults( String filter ) throws NamingException
    {
        Set<SearchResult> results = new HashSet<SearchResult>();
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.ONELEVEL_SCOPE );
        NamingEnumeration<SearchResult> namingEnumeration = ctx.search( "ou=actors,ou=system", filter, controls );
        while( namingEnumeration.hasMore() )
        {
            results.add( namingEnumeration.next() );
        }
        
        return results;
    }
}
