/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.shared.kerberos.codec.krbSafeBody.actions;


import org.apache.directory.shared.asn1.ber.Asn1Container;
import org.apache.directory.shared.kerberos.codec.actions.AbstractReadHostAddress;
import org.apache.directory.shared.kerberos.codec.krbSafeBody.KrbSafeBodyContainer;
import org.apache.directory.shared.kerberos.components.HostAddress;


/**
 * Store the s-address of KrbSafeBody.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class StoreSenderAddress extends AbstractReadHostAddress
{
    public StoreSenderAddress()
    {
        super( "KRB-SAFE-BODY s-address" );
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void setAddress( HostAddress hostAddress, Asn1Container container )
    {
        KrbSafeBodyContainer krbSafeBodyContainer = ( KrbSafeBodyContainer ) container;
        krbSafeBodyContainer.getKrbSafeBody().setSenderAddress( hostAddress );
        
        container.setGrammarEndAllowed( true );
    }
}
