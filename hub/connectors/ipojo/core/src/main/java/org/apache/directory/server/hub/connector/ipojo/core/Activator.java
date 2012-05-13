package org.apache.directory.server.hub.connector.ipojo.core;


import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;


public class Activator implements BundleActivator
{
    @Override
    public void start( BundleContext context ) throws Exception
    {
        IPojoConnector.connectorContext = context;
    }


    @Override
    public void stop( BundleContext context ) throws Exception
    {
        IPojoConnector.connectorContext = null;
    }

}