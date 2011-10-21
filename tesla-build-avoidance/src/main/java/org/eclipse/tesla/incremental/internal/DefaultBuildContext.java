package org.eclipse.tesla.incremental.internal;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeSet;

import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.BuildException;
import org.eclipse.tesla.incremental.Digester;
import org.eclipse.tesla.incremental.PathSet;

class DefaultBuildContext
    implements BuildContext
{

    private final DefaultBuildContextManager manager;

    private boolean closed = false;

    private boolean committed = false;

    private final Logger log;

    private final File outputDirectory;

    private final BuildState buildState;

    private final Collection<File> deletedInputs;

    private final Map<File, Collection<File>> addedOutputs;

    private final Map<File, Collection<File>> referencedInputs;

    private final Collection<File> modifiedOutputs;

    private final Collection<File> unmodifiedOutputs;

    private final Collection<PathSet> inputSets;

    private final long start;

    private final boolean fullBuild;

    private final Map<File, Collection<Message>> messages;

    private byte[] configuration;

    public DefaultBuildContext( DefaultBuildContextManager manager, File outputDirectory, BuildState buildState,
                                boolean fullBuild )
    {
        if ( manager == null )
        {
            throw new IllegalArgumentException( "build context factory not specified" );
        }
        if ( outputDirectory == null )
        {
            throw new IllegalArgumentException( "output directory not specified" );
        }
        if ( buildState == null )
        {
            throw new IllegalArgumentException( "build state not specified" );
        }

        start = System.currentTimeMillis();

        this.log = manager.log;
        this.manager = manager;
        this.outputDirectory = outputDirectory;
        this.buildState = buildState;
        this.fullBuild = fullBuild;

        this.deletedInputs = new TreeSet<File>( Collections.reverseOrder() );
        this.addedOutputs = new HashMap<File, Collection<File>>();
        this.referencedInputs = new HashMap<File, Collection<File>>();
        this.modifiedOutputs = new HashSet<File>();
        this.unmodifiedOutputs = new HashSet<File>();
        this.inputSets = new HashSet<PathSet>();
        this.messages = new HashMap<File, Collection<Message>>();
    }

    public Digester newDigester()
    {
        failIfCommittedOrClosed();

        return manager.newDigester( outputDirectory );
    }

    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    public Serializable getValue( Serializable key )
    {
        failIfCommittedOrClosed();

        return buildState.getValue( key );
    }

    public <T extends Serializable> T getValue( Serializable key, Class<T> valueType )
    {
        if ( valueType == null )
        {
            throw new IllegalArgumentException( "value type not specified" );
        }

        Object value = getValue( key );

        try
        {
            return valueType.cast( value );
        }
        catch ( ClassCastException e )
        {
            return null;
        }
    }

    public void setValue( Serializable key, Serializable value )
    {
        failIfCommittedOrClosed();

        buildState.setValue( key, value );
    }

    public boolean setConfiguration( byte[] digest )
    {
        failIfCommittedOrClosed();

        this.configuration = digest;

        return buildState.isConfigurationChanged( digest );
    }

    public synchronized Collection<String> getInputs( PathSet paths )
    {
        failIfCommittedOrClosed();

        if ( paths == null )
        {
            throw new IllegalArgumentException( "path set not specified" );
        }

        inputSets.add( new PathSet( paths ) );

        boolean fullBuild = this.fullBuild || buildState.isConfigurationChanged( this.configuration );

        InputResolutionContext context = new DefaultInputResolutionContext( this, paths, fullBuild, buildState );

        Collection<String> inputs = new ArrayList<String>();

        for ( Path path : manager.resolveInputs( context ) )
        {
            File inputFile = new File( paths.getBasedir(), path.getPath() );

            if ( path.isDeleted() )
            {
                deletedInputs.add( inputFile );
            }
            else
            {
                if ( addedOutputs.get( inputFile ) == null )
                {
                    addedOutputs.put( inputFile, Collections.<File> emptySet() );
                }

                inputs.add( path.getPath() );
            }
        }

        return inputs;
    }

    public OutputStream newOutputStream( File output )
        throws FileNotFoundException
    {
        failIfCommittedOrClosed();

        output = FileUtils.resolve( output, null );

        return new IncrementalFileOutputStream( output, this );
    }

    public void addOutput( File input, File output )
    {
        failIfCommittedOrClosed();

        if ( output != null )
        {
            addOutputs( Collections.singleton( output ), input );
        }
    }

    public void addOutputs( File input, File... outputs )
    {
        failIfCommittedOrClosed();

        if ( outputs != null && outputs.length > 0 )
        {
            addOutputs( Arrays.asList( outputs ), input );
        }
    }

    public void addOutputs( File input, Collection<File> outputs )
    {
        failIfCommittedOrClosed();

        addOutputs( outputs, input );
    }

    public void addOutputs( File input, PathSet outputs )
    {
        failIfCommittedOrClosed();

        if ( outputs != null )
        {
            addOutputs( manager.resolveOutputs( outputs ), input );
        }
    }

    private synchronized void addOutputs( Collection<File> outputs, File input )
    {
        input = FileUtils.resolve( input, null );

        Collection<File> addedOutputs = null;
        if ( input != null )
        {
            addedOutputs = this.addedOutputs.get( input );
            if ( addedOutputs == null || addedOutputs.isEmpty() )
            {
                addedOutputs = new TreeSet<File>();
                this.addedOutputs.put( input, addedOutputs );
            }
        }

        if ( outputs != null )
        {
            for ( File output : outputs )
            {
                if ( output != null )
                {
                    output = FileUtils.resolve( output, null );

                    modifiedOutputs.add( output );

                    if ( addedOutputs != null )
                    {
                        addedOutputs.add( output );
                    }
                }
            }
        }
    }

    synchronized void addOutput( File output, boolean modified )
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

    public synchronized void close()
    {
        if ( !committed )
        {
            manager.destroy( buildState );
        }

        closed = true;
    }

    public synchronized void commit()
    {
        if ( closed )
        {
            throw new IllegalStateException( "commit() after close()" );
        }

        if ( committed )
        {
            return;
        }

        committed = true;

        modifiedOutputs.removeAll( unmodifiedOutputs );
        int produced = modifiedOutputs.size();

        buildState.setConfiguration( configuration );

        int deletedObsolete = 0;
        for ( Map.Entry<File, Collection<File>> entry : addedOutputs.entrySet() )
        {
            File input = entry.getKey();
            buildState.setReferencedInputs( input, referencedInputs.get( input ) );
            Collection<File> outputs = entry.getValue();
            Collection<File> obsoleteOutputs = buildState.setOutputs( input, outputs );
            modifiedOutputs.addAll( obsoleteOutputs );
            deletedObsolete += deleteSuperfluousOutputs( obsoleteOutputs, "obsolete" );
        }

        int deletedOrphaned = 0;
        for ( File deletedInput : deletedInputs )
        {
            Collection<File> orphanedOutputs = buildState.removeInput( deletedInput );
            modifiedOutputs.addAll( orphanedOutputs );
            deletedOrphaned += deleteSuperfluousOutputs( orphanedOutputs, "orphaned" );
        }

        buildState.cleanupReferencedInputs();

        Map<File, Collection<Message>> oldMessages = buildState.mergeMessages( messages );

        save();

        if ( !modifiedOutputs.isEmpty() )
        {
            manager.outputUpdated( modifiedOutputs );
        }

        if ( log.isDebugEnabled() )
        {
            long millis = System.currentTimeMillis() - start;
            int errorDelta = getMessageCount( messages ) - getMessageCount( oldMessages );
            log.debug( produced + " outputs produced, " + deletedObsolete + " obsolete outputs deleted, "
                + deletedOrphaned + " orphaned outputs deleted, " + errorDelta + " messages, " + millis + " ms" );
        }

        // replay old messages
        for ( Map.Entry<File, Collection<Message>> messages : buildState.getSelectedMessages( inputSets, oldMessages ).entrySet() )
        {
            for ( Message message : messages.getValue() )
            {
                manager.logMessage( messages.getKey(), message.getLine(), message.getColumn(), message.getMessage(),
                                    message.getSeverity(), message.getCause() );
            }
        }

        int errors = buildState.getErrors( inputSets );
        if ( errors > 0 )
        {
            throw new BuildException( errors + " error" + ( errors == 1 ? "" : "s" )
                + " encountered, please see previous log/builds for more details" );
        }
    }

    private static int getMessageCount( Map<File, Collection<Message>> messages )
    {
        int count = 0;
        for ( Collection<Message> message : messages.values() )
        {
            count += message.size();
        }
        return count;
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
        if ( log.isDebugEnabled() && buildState.isStale() )
        {
            log.debug( "Concurrent modification of build state file " + buildState.getStateFile().toString() );
        }

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
        failIfCommittedOrClosed();

        input = FileUtils.resolve( input, null );

        Collection<Message> messages = this.messages.get( input );

        if ( messages == null )
        {
            throw new IllegalStateException( "addMessage without prio clearMessages" );
        }

        messages.add( new Message( line, column, message, severity, cause ) );

        manager.addMessage( input, line, column, message, severity, cause );
    }

    public void clearMessages( File input )
    {
        failIfCommittedOrClosed();

        input = FileUtils.resolve( input, null );

        buildState.clearErrors( input );

        messages.put( input, new ArrayList<Message>() );

        manager.clearMessages( input );
    }

    private void failIfCommittedOrClosed()
    {
        if ( committed )
        {
            throw new IllegalStateException( "build context has already been committed" );
        }
        if ( closed )
        {
            throw new IllegalStateException( "build context has already been closed" );
        }
    }

    public void addReferencedInputs( File input, Collection<File> referencedInputs )
    {
        if ( referencedInputs != null )
        {
            input = FileUtils.resolve( input, null );

            Collection<File> resolvedReferencedInputs = this.referencedInputs.get( input );
            if ( resolvedReferencedInputs == null )
            {
                resolvedReferencedInputs = new HashSet<File>();
                this.referencedInputs.put( input, resolvedReferencedInputs );
            }

            for ( File referencedInput : referencedInputs )
            {
                resolvedReferencedInputs.add( FileUtils.resolve( referencedInput, null ) );
            }
        }
    }
}
