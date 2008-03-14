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
package org.apache.directory.server.core.avltree;


import org.junit.Test;
import org.apache.directory.shared.ldap.util.ArrayUtils;
import static junit.framework.Assert.*;

import java.io.Serializable;


/**
 * Test case for the default Marshaller implementation.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $$Rev$$
 */
public class DefaultMarshallerTest
{
    DefaultMarshaller marshaller = DefaultMarshaller.INSTANCE;


    @Test
    public void testRoundTrip() throws Exception
    {
        byte[] serialized = marshaller.serialize( "test" );
        Object deserialized = marshaller.deserialize( serialized );
        assertEquals( "test", deserialized );
        assertTrue( ArrayUtils.isEquals( serialized, marshaller.serialize( deserialized ) ) );
    }


    @Test
    public void testRoundTripComplex() throws Exception
    {
        byte[] serialized = marshaller.serialize( new Bar() );
        Object deserialized = marshaller.deserialize( serialized );
        assertNotNull( deserialized );
        assertTrue( ArrayUtils.isEquals( serialized, marshaller.serialize( deserialized ) ) );
    }


    static class Bar implements Serializable
    {
        int intValue = 37;
        String stringValue = "bar";
        long longValue = 32L;
        Foo fooValue = new Foo();
    }


    static class Foo implements Serializable
    {
        float floatValue = 3;
        String stringValue = "foo";
        double doubleValue = 1.2;
        byte byteValue = 3;
        char charValue = 'a';
    }
}
