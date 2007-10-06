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
package org.apache.directory.server.tools.commands.storedprocedurecmd;

import org.apache.directory.server.configuration.ApacheDS;
import org.apache.directory.server.tools.ToolCommandListener;
import org.apache.directory.server.tools.execution.BaseToolCommandExecutor;
import org.apache.directory.server.tools.util.ListenerParameter;
import org.apache.directory.server.tools.util.Parameter;
import org.apache.directory.shared.ldap.constants.JndiPropertyConstants;
import org.apache.directory.shared.ldap.message.extended.StoredProcedureRequest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * This is the Executor Class of the Stored Procedure Command.
 * 
 * The command can be called using the 'execute' method.
 */
public class StoredProcedureCommandExecutor extends BaseToolCommandExecutor
{

    public StoredProcedureCommandExecutor()
    {
        super( "proc" );
    }

    /**
     * Executes the command.
     * <p>
     * Use the following Parameters and ListenerParameters to call the command.
     * <p>
     * Parameters : <ul>
     *      <li>"HOST_PARAMETER" with a value of type 'String', representing server host</li>
     *      <li>"PORT_PARAMETER" with a value of type 'Integer', representing server port</li>
     *      <li>"PASSWORD_PARAMETER" with a value of type 'String', representing user password</li>
     *      <li>"DEBUG_PARAMETER" with a value of type 'Boolean', true to enable debug</li>
     *      <li>"QUIET_PARAMETER" with a value of type 'Boolean', true to enable quiet</li>
     *      <li>"VERBOSE_PARAMETER" with a value of type 'Boolean', true to enable verbose</li>
     * </ul>
     * <br />
     * ListenersParameters : <ul>
     *      <li>"OUTPUTLISTENER_PARAMETER", a listener that will receive all output messages. It returns
     *          messages as a String.</li>
     *      <li>"ERRORLISTENER_PARAMETER", a listener that will receive all error messages. It returns messages
     *          as a String.</li>
     *      <li>"EXCEPTIONLISTENER_PARAMETER", a listener that will receive all exception(s) raised. It returns
     *          Exceptions.</li>
     *      <li>"ENTRYADDEDLISTENER_PARAMETER", a listener that will receive a message each time an entry is
     *          added. It returns the DN of the added entry as a String.</li>
     *      <li>"ENTRYADDFAILEDLISTENER_PARAMETER", a listener that will receive a message each time an entry 
     *          has failed to import. It returns the DN og the entry on error.</li>
     * </ul>
     */
    public void execute( Parameter[] params, ListenerParameter[] listeners )
    {
        processParameters( params );
        processListeners( listeners );

        try
        {
            execute();
        }
        catch ( Exception e )
        {
            notifyExceptionListener( e );
        }
    }
    
    private void execute() throws Exception
    {
        if ( isDebugEnabled() )
        {
            notifyOutputListener( "Parameters for StoredProcedure extended request:" );
            notifyOutputListener( "port = " + port );
            notifyOutputListener( "host = " + host );
            notifyOutputListener( "password = " + password );
        }

        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put( JndiPropertyConstants.JNDI_FACTORY_INITIAL, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( JndiPropertyConstants.JNDI_PROVIDER_URL, "ldap://" + host + ":" + port );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( JndiPropertyConstants.JNDI_SECURITY_CREDENTIALS, password );
        env.put( JndiPropertyConstants.JNDI_SECURITY_AUTHENTICATION, "simple" );

        LdapContext ctx = new InitialLdapContext( env, null );
        if ( !isQuietEnabled() )
        {
            notifyOutputListener( "Connection to the server established.\n"
                + "Sending extended request and blocking for shutdown:" );
        }

        String language = "java";
        String procedure = "HelloWorldProcedure.sayHello";
        StoredProcedureRequest req = new StoredProcedureRequest( 0, procedure, language );
        ctx.extendedOperation( req );
        ctx.close();
    }

    private void processParameters( Parameter[] params )
    {
        Map<String, Object> parameters = new HashMap<String, Object>();
        
        for ( Parameter parameter:params )
        {
            parameters.put( parameter.getName(), parameter.getValue() );
        }

        // Quiet param
        Boolean quietParam = ( Boolean ) parameters.get( QUIET_PARAMETER );
        
        if ( quietParam != null )
        {
            setQuietEnabled( quietParam );
        }

        // Debug param
        Boolean debugParam = ( Boolean ) parameters.get( DEBUG_PARAMETER );
        
        if ( debugParam != null )
        {
            setDebugEnabled( debugParam );
        }

        // Verbose param
        Boolean verboseParam = ( Boolean ) parameters.get( VERBOSE_PARAMETER );
        
        if ( verboseParam != null )
        {
            setVerboseEnabled( verboseParam );
        }

        // Install-path param
        String installPathParam = ( String ) parameters.get( INSTALLPATH_PARAMETER );
        
        if ( installPathParam != null )
        {
            try
            {
                setLayout( installPathParam );
                
                if ( !isQuietEnabled() )
                {
                    notifyOutputListener( "loading settings from: " + getLayout().getConfigurationFile() );
                }
                
                URL configUrl = getLayout().getConfigurationFile().toURI().toURL();
                ApplicationContext factory = new FileSystemXmlApplicationContext( configUrl.toString() );
                setApacheDS( ( ApacheDS ) factory.getBean( "apacheDS" ) );
            }
            catch ( MalformedURLException e )
            {
                notifyErrorListener( e.getMessage() );
                notifyExceptionListener( e );
            }
        }

        // Host param
        String hostParam = ( String ) parameters.get( HOST_PARAMETER );
        if ( hostParam != null )
        {
            host = hostParam;
        }
        else
        {
            host = DEFAULT_HOST;

            if ( isDebugEnabled() )
            {
                notifyOutputListener( "host set to default: " + host );
            }
        }

        // Port param
        Integer portParam = ( Integer ) parameters.get( PORT_PARAMETER );
        
        if ( portParam != null )
        {
            port = portParam;
        }
        else if ( getApacheDS() != null )
        {
            port = getApacheDS().getLdapConfiguration().getIpPort();

            if ( isDebugEnabled() )
            {
                notifyOutputListener( "port overriden by server.xml configuration: " + port );
            }
        }
        else
        {
            port = DEFAULT_PORT;

            if ( isDebugEnabled() )
            {
                notifyOutputListener( "port set to default: " + port );
            }
        }

        // Password param
        String passwordParam = ( String ) parameters.get( PASSWORD_PARAMETER );
        
        if ( passwordParam != null )
        {
            password = passwordParam;
        }
        else
        {
            password = DEFAULT_PASSWORD;

            if ( isDebugEnabled() )
            {
                notifyOutputListener( "password set to default: " + password );
            }
        }
    }
    
    private void processListeners( ListenerParameter[] listeners )
    {
        Map<String, ToolCommandListener> parameters = new HashMap<String, ToolCommandListener>();
        
        for ( ListenerParameter parameter:listeners )
        {
            parameters.put( parameter.getName(), parameter.getListener() );
        }

        // OutputListener param
        ToolCommandListener outputListener = parameters.get( OUTPUTLISTENER_PARAMETER );
        
        if ( outputListener != null )
        {
            this.outputListener = outputListener;
        }

        // ErrorListener param
        ToolCommandListener errorListener = parameters.get( ERRORLISTENER_PARAMETER );
        
        if ( errorListener != null )
        {
            this.errorListener = errorListener;
        }

        // ExceptionListener param
        ToolCommandListener exceptionListener = parameters.get( EXCEPTIONLISTENER_PARAMETER );
        
        if ( exceptionListener != null )
        {
            this.exceptionListener = exceptionListener;
        }
    }
}
