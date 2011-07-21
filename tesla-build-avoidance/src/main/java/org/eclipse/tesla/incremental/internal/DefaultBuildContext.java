package org.eclipse.tesla.incremental.internal;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.Digester;
import org.eclipse.tesla.incremental.PathSet;

class DefaultBuildContext
    implements BuildContext
{

    private final PathSetResolver pathSetResolver;

    private final MessageHandler messageHandler;

    private final OutputListener outputListener;

    private final Logger log;

    private final File outputDirectory;

    private final BuildState buildState;

    private Collection<File> deletedInputs;

    private Map<File, Collection<File>> addedOutputs;

    private Collection<File> modifiedOutputs;

    private Collection<File> unmodifiedOutputs;

    private long start;

    public DefaultBuildContext( File outputDirectory, BuildState buildState, PathSetResolver pathSetResolver,
                                MessageHandler messageHandler, OutputListener outputListener, Logger log )
    {
        if ( outputDirectory == null )
        {
            throw new IllegalArgumentException( "output directory not specified" );
        }
        if ( buildState == null )
        {
            throw new IllegalArgumentException( "build state not specified" );
        }
        if ( pathSetResolver == null )
        {
            throw new IllegalArgumentException( "path set resolver not specified" );
        }

        start = System.currentTimeMillis();

        this.outputDirectory = outputDirectory.getAbsoluteFile();
        this.buildState = buildState;
        this.pathSetResolver = pathSetResolver;
        this.messageHandler = ( messageHandler != null ) ? messageHandler : NullMessageHandler.INSTANCE;
        this.outputListener = ( outputListener != null ) ? outputListener : NullOutputListener.INSTANCE;
        this.log = ( log != null ) ? log : NullLogger.INSTANCE;

        this.deletedInputs = new TreeSet<File>( Collections.reverseOrder() );
        this.addedOutputs = new HashMap<File, Collection<File>>();
        this.modifiedOutputs = new HashSet<File>();
        this.unmodifiedOutputs = new HashSet<File>();
    }

    public Digester newDigester()
    {
        return new DefaultDigester();
    }

    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    public boolean setConfiguration( PathSet paths, byte[] digest )
    {
        return buildState.setConfiguration( paths, digest );
    }

    public Collection<String> getInputs( PathSet paths, boolean fullBuild )
    {
        PathSetResolutionContext context = new DefaultPathSetResolutionContext( this, paths, fullBuild, buildState );

        Collection<String> inputs = new ArrayList<String>();

        for ( Path path : pathSetResolver.resolve( context ) )
        {
            if ( path.isDeleted() )
            {
                deletedInputs.add( new File( paths.getBasedir(), path.getPath() ) );
            }
            else
            {
                inputs.add( path.getPath() );
            }
        }

        return inputs;
    }

    public OutputStream newOutputStream( File output )
        throws IOException
    {
        if ( output == null )
        {
            throw new IllegalArgumentException( "output file not specified" );
        }
        output = FileUtils.resolve( output, getOutputDirectory() );
        output.getParentFile().mkdirs();
        return new IncrementalFileOutputStream( output, this );
    }

    public OutputStream newOutputStream( String output )
        throws IOException
    {
        if ( output == null )
        {
            throw new IllegalArgumentException( "output file not specified" );
        }
        return newOutputStream( new File( output ) );
    }

    public void addOutputs( File input, File... outputs )
    {
        if ( outputs == null || outputs.length <= 0 )
        {
            return;
        }

        Collection<File> resolvedOutputs = new ArrayList<File>( outputs.length );
        for ( File output : outputs )
        {
            File resolvedOutput = FileUtils.resolve( output, getOutputDirectory() );
            resolvedOutputs.add( resolvedOutput );
        }

        addOutputs( resolvedOutputs, input );
    }

    public void addOutputs( File input, String... outputs )
    {
        if ( outputs == null || outputs.length <= 0 )
        {
            return;
        }

        Collection<File> resolvedOutputs = new ArrayList<File>( outputs.length );
        for ( String output : outputs )
        {
            File resolvedOutput = FileUtils.resolve( new File( output ), getOutputDirectory() );
            resolvedOutputs.add( resolvedOutput );
        }

        addOutputs( resolvedOutputs, input );
    }

    private void addOutputs( Collection<File> outputs, File input )
    {
        input = input.getAbsoluteFile();

        Collection<File> addedOutputs = this.addedOutputs.get( input );
        if ( addedOutputs == null )
        {
            addedOutputs = new TreeSet<File>();
            this.addedOutputs.put( input, addedOutputs );
        }
        addedOutputs.addAll( outputs );

        modifiedOutputs.addAll( outputs );
    }

    void addOutput( File output, boolean modified )
    {
        if ( modified )
        {
            modifiedOutputs.add( output );
        }
        else
        {
            unmodifiedOutputs.add( output );
        }
    }

    public void finish()
    {
        modifiedOutputs.removeAll( unmodifiedOutputs );

        int deletedObsolete = 0;
        for ( Map.Entry<File, Collection<File>> entry : addedOutputs.entrySet() )
        {
            File input = entry.getKey();
            Collection<File> outputs = entry.getValue();
            Collection<File> obsoleteOutputs = buildState.setOutputs( input, outputs );
            deletedObsolete += deleteSuperfluousOutputs( obsoleteOutputs, "obsolete" );
        }

        int deletedOrphaned = 0;
        for ( File deletedInput : deletedInputs )
        {
            Collection<File> orphanedOutputs = buildState.removeInput( deletedInput );
            deletedOrphaned += deleteSuperfluousOutputs( orphanedOutputs, "orphaned" );
        }

        save();

        outputListener.outputUpdated( modifiedOutputs );

        if ( log.isDebugEnabled() )
        {
            long millis = System.currentTimeMillis() - start;
            log.debug( modifiedOutputs.size() + " outputs produced, " + deletedObsolete + " obsolete outputs deleted, "
                + deletedOrphaned + " orphaned outputs deleted, " + millis + " ms" );
        }
    }

    private int deleteSuperfluousOutputs( Collection<File> outputs, String type )
    {
        int deleted = 0;
        if ( outputs != null && !outputs.isEmpty() )
        {
            for ( File output : outputs )
            {
                if ( output.delete() )
                {
                    deleted++;
                    log.debug( "Deleted " + type + " output " + output );
                }
                else if ( output.exists() )
                {
                    log.debug( "Failed to delete " + type + " output " + output );
                }
            }
        }
        return deleted;
    }

    private void save()
    {
        try
        {
            buildState.save();
        }
        catch ( IOException e )
        {
            log.warn( "Could not serialize incremental build state to " + buildState.getStateFile(),
                      log.isDebugEnabled() ? e : null );
        }
    }

    public void addMessage( File input, int line, int column, String message, int severity, Throwable cause )
    {
        messageHandler.addMessage( input, line, column, message, severity, cause );
    }

    public void clearMessages( File input )
    {
        messageHandler.clearMessages( input );
    }

}
