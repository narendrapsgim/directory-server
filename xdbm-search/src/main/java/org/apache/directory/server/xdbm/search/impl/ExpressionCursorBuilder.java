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
package org.apache.directory.server.xdbm.search.impl;


import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.xdbm.ForwardIndexEntry;
import org.apache.directory.server.xdbm.Index;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.core.partition.impl.btree.IndexAssertion;
import org.apache.directory.server.core.partition.impl.btree.IndexAssertionEnumeration;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.filter.AndNode;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.AssertionNode;
import org.apache.directory.shared.ldap.filter.BranchNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ExtensibleNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LeafNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.NotNode;
import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;


/**
 * Builds Cursors over candidates that satisfy a filter expression.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ExpressionCursorBuilder<E> implements CursorBuilder<E>
{
    /** The database used by this enumerator */
    private Store<E> db = null;
    /** CursorBuilder flyweight for evaulating filter scope assertions */
    private ScopeCursorBuilder<E> scopeEnumerator;
    /** CursorBuilder flyweight for evaulating filter substring assertions */
    private SubstringCursorBuilder<E> substringEnumerator;
    /** Evaluator dependency on a ExpressionEvaluatorBuilder */
    private ExpressionEvaluatorBuilder<E> evaluatorBuilder;


    /**
     * Creates an expression tree enumerator.
     *
     * @param db database used by this enumerator
     * @param evaluatorBuilder
     */
    public ExpressionCursorBuilder(BTreePartition db, AttributeTypeRegistry attributeTypeRegistry,
        ExpressionEvaluatorBuilder evaluatorBuilder )
    {
        this.db = db;
        this.evaluatorBuilder = evaluatorBuilder;

        LeafEvaluator leafEvaluator = evaluatorBuilder.getLeafEvaluator();
        scopeEnumerator = new ScopeCursorBuilder( db, leafEvaluator.getScopeEvaluator() );
        substringEnumerator = new SubstringCursorBuilder( db, attributeTypeRegistry, leafEvaluator.getSubstringEvaluator() );
    }


    /**
     * Creates an enumeration to enumerate through the set of candidates 
     * satisfying a filter expression.
     * 
     * @param node a filter expression root
     * @return an enumeration over the 
     * @throws NamingException if database access fails
     */
    public NamingEnumeration<ForwardIndexEntry> enumerate( ExprNode node ) throws NamingException
    {
    	NamingEnumeration<ForwardIndexEntry> list = null;

        if ( node instanceof ScopeNode )
        {
            list = scopeEnumerator.enumerate( node );
        }
        else if ( node instanceof AssertionNode )
        {
            throw new IllegalArgumentException( "Cannot produce enumeration " + "on an AssertionNode" );
        }
        else if ( node.isLeaf() )
        {
            LeafNode leaf = ( LeafNode ) node;

            if ( node instanceof PresenceNode )
            {
                list = enumPresence( ( PresenceNode ) node );
            }
            else if ( node instanceof EqualityNode )
            {
                list = enumEquality( ( EqualityNode ) node );
            }
            else if ( node instanceof GreaterEqNode )
            {
                list = enumGreaterOrLesser( ( SimpleNode ) node, SimpleNode.EVAL_GREATER );
            }
            else if ( node instanceof LessEqNode )
            {
                list = enumGreaterOrLesser( ( SimpleNode ) node, SimpleNode.EVAL_LESSER );
            }
            else if ( node instanceof SubstringNode )
            {
                list = substringEnumerator.enumerate( leaf );
            }
            else if ( node instanceof ExtensibleNode )
            {
                // N O T   I M P L E M E N T E D   Y E T !
                throw new NotImplementedException();
            }
            else if ( node instanceof ApproximateNode )
            {
                list = enumEquality( ( EqualityNode ) node );
            }
            else
            {
                throw new IllegalArgumentException( "Unknown leaf assertion" );
            }
        }
        else
        {
            BranchNode branch = ( BranchNode ) node;

            if ( node instanceof AndNode )
            {
                list = enumConj( (AndNode)branch );
            }
            else if ( node instanceof OrNode )
            {
                list = enumDisj( (OrNode)branch );
            }
            else if ( node instanceof NotNode )
            {
                list = enumNeg( (NotNode)branch );
            }
            else
            {
                throw new IllegalArgumentException( "Unknown branch logical operator" );
            }
        }

        return list;
    }


    /**
     * Creates an enumeration over a disjunction expression branch node.
     *
     * @param node the disjunction expression branch node
     */
    private NamingEnumeration<ForwardIndexEntry> enumDisj( OrNode node ) throws NamingException
    {
        List<ExprNode> children = node.getChildren();
        NamingEnumeration<IndexRecord>[] childEnumerations = new NamingEnumeration[children.size()];

        // Recursively create NamingEnumerations for each child expression node
        for ( int ii = 0; ii < childEnumerations.length; ii++ )
        {
            childEnumerations[ii] = enumerate( children.get( ii ) );
        }

        return new OrCursor( childEnumerations );
    }


    /**
     * Creates an enumeration over a negation expression branch node.
     *
     * @param node a negation expression branch node
     */
    private NamingEnumeration<ForwardIndexEntry> enumNeg( final BranchNode node ) throws NamingException
    {
    	NamingEnumeration<ForwardIndexEntry> baseEnumeration = null;
    	NamingEnumeration<ForwardIndexEntry> enumeration = null;

        try
        {
            baseEnumeration = db.getNdnIndex().listIndices();
        }
        catch ( java.io.IOException e )
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        IndexAssertion assertion = new IndexAssertion()
        {
            public boolean assertCandidate( IndexEntry rec ) throws NamingException
            {
                // NOTICE THE ! HERE
                // The candidate is valid if it does not pass assertion. A
                // candidate that passes assertion is therefore invalid.
                return !evaluatorBuilder.evaluate( node.getFirstChild(), rec );
            }
        };

        enumeration = new IndexAssertionEnumeration( baseEnumeration, assertion, true );
        return enumeration;
    }


    /**
     * Creates an enumeration over a conjunction expression branch node.
     *
     * @param node a conjunction expression branch node
     */
    private NamingEnumeration<ForwardIndexEntry> enumConj( final AndNode node ) throws NamingException
    {
        int minIndex = 0;
        long minValue = Long.MAX_VALUE;
        long value = Long.MAX_VALUE;

        /*
         * We scan the child nodes of a branch node searching for the child
         * expression node with the smallest scan count.  This is the child
         * we will use for iteration by creating a NamingEnumeration over its
         * expression.
         */
        final List<ExprNode> children = node.getChildren();
        
        for ( int ii = 0; ii < children.size(); ii++ )
        {
            ExprNode child = children.get( ii );
            value = ( Long ) child.get( "count" );
            minValue = Math.min( minValue, value );

            if ( minValue == value )
            {
                minIndex = ii;
            }
        }

        // Once found we build the child enumeration & the wrapping enum
        final ExprNode minChild = children.get( minIndex );
        IndexAssertion assertion = new IndexAssertion()
        {
            public boolean assertCandidate( IndexEntry rec ) throws NamingException
            {
                for ( int ii = 0; ii < children.size(); ii++ )
                {
                    ExprNode child = children.get( ii );

                    // Skip the child (with min scan count) chosen for enum
                    if ( child == minChild )
                    {
                        continue;
                    }
                    else if ( !evaluatorBuilder.evaluate( child, rec ) )
                    {
                        return false;
                    }
                }

                return true;
            }
        };

        // Do recursive call to build child enumeration then wrap and return
        NamingEnumeration<ForwardIndexEntry> underlying = enumerate( minChild );
        IndexAssertionEnumeration iae;
        iae = new IndexAssertionEnumeration( underlying, assertion );
        return iae;
    }


    /**
     * Returns an enumeration over candidates that satisfy a presence attribute 
     * value assertion.
     * 
     * @param node the presence AVA node
     * @return an enumeration over the index records matching the AVA
     * @throws NamingException if there is a failure while accessing the db
     */
    private NamingEnumeration<ForwardIndexEntry> enumPresence( final PresenceNode node ) throws NamingException
    {
        if ( db.hasUserIndexOn( node.getAttribute() ) )
        {
            Index idx = db.getExistanceIndex();
            try
            {
                return idx.listIndices( node.getAttribute() );
            }
            catch ( java.io.IOException e )
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        return nonIndexedScan( node );
    }


    /**
     * Returns an enumeration over candidates that satisfy a simple greater than
     * or less than or equal to attribute value assertion.
     * 
     * @param node the AVA node
     * @param isGreater true if >= false if <= is used
     * @return an enumeration over the index records matching the AVA
     * @throws NamingException if there is a failure while accessing the db
     */
    private NamingEnumeration<ForwardIndexEntry> enumGreaterOrLesser( final SimpleNode node, final boolean isGreaterOrLesser ) throws NamingException
    {
        if ( db.hasUserIndexOn( node.getAttribute() ) )
        {
            Index idx = db.getUserIndex( node.getAttribute() );

            try
            {
                return idx.listIndices( node.getValue(), isGreaterOrLesser );
            }
            catch ( java.io.IOException e )
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        return nonIndexedScan( node );
    }


    /**
     * Returns an enumeration over candidates that satisfy a simple equality 
     * attribute value assertion.
     * 
     * @param node the equality AVA node
     * @return an enumeration over the index records matching the AVA
     * @throws NamingException if there is a failure while accessing the db
     */
    private NamingEnumeration<ForwardIndexEntry> enumEquality( final EqualityNode node ) throws NamingException
    {
        if ( db.hasUserIndexOn( node.getAttribute() ) )
        {
            Index idx = db.getUserIndex( node.getAttribute() );
            try
            {
                return idx.listIndices( node.getValue() );
            }
            catch ( java.io.IOException e )
            {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }

        return nonIndexedScan( node );
    }


    /**
     * Creates a scan over all entries in the database with an assertion to test
     * for the correct evaluation of a filter expression on a LeafNode.
     * 
     * @param node the leaf node to produce a scan over
     * @return the enumeration over all perspective candidates satisfying expr
     * @throws NamingException if db access failures result
     */
    private NamingEnumeration<ForwardIndexEntry> nonIndexedScan( final LeafNode node ) throws NamingException
    {
        try
        {
            NamingEnumeration<ForwardIndexEntry> underlying = db.getNdnIndex().listIndices();
        }
        catch ( java.io.IOException e )
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        IndexAssertion assertion = new IndexAssertion()
        {
            public boolean assertCandidate( IndexEntry entry ) throws NamingException
            {
                return evaluatorBuilder.getLeafEvaluator().evaluate( node, entry );
            }
        };

        return new IndexAssertionEnumeration( underlying, assertion );
    }
}
