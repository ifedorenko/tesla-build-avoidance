package org.eclipse.tesla.incremental;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.File;

/**
 * Provides incremental build support for code generators and similar tooling that produces output from some input
 * files. The general usage pattern is demonstrated by this simplified example snippet:
 * 
 * <pre>
 * BuildContext buildContext = buildContextManager.newContext( outDir, stateDir, &quot;my-plugin&quot; );
 * try
 * {
 *     PathSet pathSet = new PathSet( inputDir, includes, excludes );
 *     byte[] configDigest = buildContext.newDigester().string( someParameter ).finish();
 *     boolean fullBuild = buildContext.setConfiguration( pathSet, configDigest );
 *     for ( String inputPath : buildContext.getInputs( pathSet, fullBuild ) )
 *     {
 *         File inputFile = new File( inputDir, inputPath );
 *         File outputFile = new File( outputDir, inputPath );
 *         // actually produce output file
 *         buildContext.addOutput( inputFile, outputFile );
 *     }
 *     buildContext.commit();
 * }
 * finally
 * {
 *     buildContext.close();
 * }
 * </pre>
 * 
 * Some methods are provided both via {@link BuildContextManager} and {@link BuildContext}. For efficiency, those
 * methods should be invoked via a {@link BuildContext} instance when possible. The equivalent methods in
 * {@link BuildContextManager} are only provided for cases when a component has no direct access to a
 * {@link BuildContext} instance, e.g. due to API constraints, but still should participate in an active build context
 * that has been created by a component higher up in the call hierarchy.
 */
public interface BuildContextManager
{

    /**
     * Creates a new build context to update files in the specified output directory.
     * 
     * @param outputDirectory The output directory for the files to be produced, must not be {@code null}.
     * @param stateDirectory The temporary directory where auxiliary state related to incremental building is stored,
     *            must not be {@code null}. This directory can safely be shared among all components/plugins producing
     *            build output and use a well-known location. However, this directory should reside in a location which
     *            gets automatically deleted during a full clean of the project output.
     * @param builderId The unique identifier of the component using the build context, must not be {@code null}.
     * @return The new build context, never {@code null}.
     */
    BuildContext newContext( File outputDirectory, File stateDirectory, String builderId );

}
