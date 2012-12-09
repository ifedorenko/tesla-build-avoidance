package org.eclipse.tesla.incremental.maven;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.PathSet;

/**
 * A simple mojo that demonstrates the use of the incremental build support.
 * 
 * @goal incremental
 * @phase process-resources
 */
public class IncrementalMojo
    extends AbstractMojo
{

    // --- usual plugin parameters ----------------------------------

    /**
     * @parameter default-value="${basedir}"
     * @readonly
     */
    private File projectDirectory;

    /**
     * @parameter default-value="src/main/tesla"
     */
    private File inputDirectory;

    /**
     * @parameter
     */
    private String[] includes;

    /**
     * @parameter
     */
    private String[] excludes;

    /**
     * @parameter default-value="${project.build.directory}/tesla"
     */
    private File outputDirectory;

    /**
     * @parameter default-value="${project.build.sourceEncoding}"
     */
    private String encoding;

    /**
     * @parameter default-value="${project.build.filters}"
     */
    private Collection<String> filters;

    /**
     * @component
     */
    private BuildContext buildContext;

    // --- mojo logic -----------------------------------------------

    public void execute()
        throws MojoExecutionException
    {
        try
        {
            Properties filterProps = new Properties();
            IOUtils.load( filterProps, filters, projectDirectory );
            filterProps.putAll( System.getProperties() );

            // set up pathset defining the input files to process
            PathSet pathset = new PathSet( inputDirectory, includes, excludes );

            // get input files that need processing
            Collection<String> paths = buildContext.getInputs( pathset );

            // process input files
            for ( String path : paths )
            {
                File inputFile = new File( pathset.getBasedir(), path );
                File outputFile = new File( outputDirectory, path );

                getLog().info( "Processing input " + path + " > " + outputFile );

                // register output files
                buildContext.addOutputs( inputFile, outputFile );

                // generate output files
                try
                {
                    buildContext.clearMessages( inputFile );
                    IOUtils.filter( inputFile, buildContext.newOutputStream( outputFile ), encoding, filterProps );
                }
                catch ( IOException e )
                {
                    buildContext.addMessage( inputFile, 0, 0, "Could not read file", BuildContext.SEVERITY_ERROR, e );
                }
            }
            if ( paths.isEmpty() )
            {
                getLog().info( "No inputs found to process" );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

}
