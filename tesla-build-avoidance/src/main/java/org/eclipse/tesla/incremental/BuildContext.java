package org.eclipse.tesla.incremental;

/*******************************************************************************
 * Copyright (c) 2011 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;

/**
 * Provides incremental build support for code generators and similar tooling that produces output from some input
 * files. The general usage pattern is demonstrated by this simplified example snippet:
 * 
 * <pre>
 * {@literal @}Inject
 * BuildContext buildContext;
 * 
 * ...
 * 
 * PathSet pathSet = new PathSet( inputDir, includes, excludes );
 * for ( String inputPath : buildContext.getInputs( pathSet ) )
 * {
 *     File inputFile = new File( inputDir, inputPath );
 *     File outputFile = new File( outputDir, inputPath );
 *     // actually produce output file
 *     buildContext.addOutput( inputFile, outputFile );
 * }
 * </pre>
 */
public interface BuildContext
    extends Closeable
{

    /**
     * Message severity to report a warning for an input file.
     * 
     * @see #addMessage(File, int, int, String, int, Throwable)
     */
    public static final int SEVERITY_WARNING = 1;

    /**
     * Message severity to report an error for an input file.
     * 
     * @see #addMessage(File, int, int, String, int, Throwable)
     */
    public static final int SEVERITY_ERROR = 2;

    /**
     * Gets the user value that has been associated with the specified key during the previous build. If a full build
     * rather than an incremental build is performed, all previously saved user data is lost/reset.
     * 
     * @param key The key used to lookup the value, must not be {@code null}.
     * @return The value associated with the key or {@code null} if none.
     */
    Serializable getValue( Serializable key );

    /**
     * Gets the user value that has been associated with the specified key during the previous build. If a full build
     * rather than an incremental build is performed, all previously saved user data is lost/reset.
     * 
     * @param key The key used to lookup the value, must not be {@code null}.
     * @param valueType The expected type of the value, must not be {@code null}.
     * @return The value associated with the key or {@code null} if none or if the existing value is not
     *         assignment-compatible with the specified value type.
     */
    <T extends Serializable> T getValue( Serializable key, Class<T> valueType );

    /**
     * Associates the specified user value with the given key for reuse during a future incremental build. The key-value
     * pair is persisted as part of the incremental build state and allows builders to preserve any auxiliary data
     * needed during incremental building.
     * 
     * @param key The key used to lookup the value, must not be {@code null}.
     * @param value The user value to save, may be {@code null}.
     */
    void setValue( Serializable key, Serializable value );

    /**
     * Determines all input files matched by the specified path set that require processing. An input file might require
     * processing because it was added or modified since the last build or because any of its previously produced
     * outputs is missing.
     * <p>
     * If configuration change was determined by the build system, all input files are considered to require processing.
     * 
     * @param paths The path set whose inputs should be analyzed for changes, must not be {@code null}.
     * @return The (possibly empty) collection of input paths that need processing to update the output directory. The
     *         paths are relative to the base directory of the path set and not in any particular order.
     */
    Collection<String> getInputs( PathSet paths );

    /**
     * Registers the specified output for an input file/directory. This method may be called repeatedly for the same
     * input file, e.g. in case a single input file produces more than one output file. If the output is not produced
     * from a particular file but rather a possibly empty collection of input files that get aggregated, the input
     * parameter should be {@code null}.<br>
     * <br>
     * <strong>Note:</strong> The various {@code addOutput*()} methods are used to describe the precise relationship
     * between input files and output files, their proper use is crucial for incremental building to work flawlessly.
     * 
     * @param input The input file/directory, may be {@code null}.
     * @param output The output file/directory, may be {@code null}.
     */
    void addOutput( File input, File output );

    /**
     * Registers the specified outputs for an input file. This method may be called repeatedly for the same input file,
     * e.g. in case a single input file produces more than one output file. If the output is not produced from a
     * particular file but rather a possibly empty collection of input files that get aggregated, the input parameter
     * should be {@code null}.<br>
     * <br>
     * <strong>Note:</strong> The various {@code addOutput*()} methods are used to describe the precise relationship
     * between input files and output files, their proper use is crucial for incremental building to work flawlessly.
     * 
     * @param input The input file/directory, may be {@code null}.
     * @param outputs The output files/directories, may be {@code null}.
     */
    void addOutputs( File input, File... outputs );

    /**
     * Registers the specified outputs for an input file. This method may be called repeatedly for the same input file,
     * e.g. in case a single input file produces more than one output file. If the output is not produced from a
     * particular file but rather a possibly empty collection of input files that get aggregated, the input parameter
     * should be {@code null}.<br>
     * <br>
     * <strong>Note:</strong> The various {@code addOutput*()} methods are used to describe the precise relationship
     * between input files and output files, their proper use is crucial for incremental building to work flawlessly.
     * 
     * @param input The input file/directory, may be {@code null}.
     * @param outputs The output files/directories, may be {@code null}.
     */
    void addOutputs( File input, Collection<File> outputs );

    /**
     * Registers the specified outputs for an input file. This method may be called repeatedly for the same input file,
     * e.g. in case a single input file produces more than one output file. If the output is not produced from a
     * particular file but rather a possibly empty collection of input files that get aggregated, the input parameter
     * should be {@code null}.<br>
     * <br>
     * <strong>Note:</strong> The various {@code addOutput*()} methods are used to describe the precise relationship
     * between input files and output files, their proper use is crucial for incremental building to work flawlessly.
     * 
     * @param input The input file/directory, may be {@code null}.
     * @param outputs The path set describing the output files/directories, may be {@code null}. <strong>Note:</strong>
     *            For incremental building to work properly, this path set should only describe the outputs from the
     *            given input and not overlap with the output of other inputs.
     */
    void addOutputs( File input, PathSet outputs );

    /**
     * Registers the specified files as referenced inputs from the input file. Referenced files, such as #include in
     * c/cpp, are part of logical input of code generation and it is assumed that reprocessing of the input file is
     * required if any of the referenced files changes.
     * 
     * @param input The input file, must not be {@code null}.
     * @param referencedInputs The referenced input files, may be {@code null}.
     */
    void addReferencedInputs( File input, Collection<File> referencedInputs );

    /**
     * Opens an output stream to the specified file. Use of this method is not obligatory but still strongly
     * recommended. Unlike the mere invocation of {@link java.io.FileInputStream#FileInputStream(File) new
     * FileInputStream( output )}, this method ensures that any parent directories of the output file are created.
     * Furthermore, the returned stream can better cooperate with the incremental build support, i.e. suppress
     * unnecessary file modifications.
     * 
     * @param output The output file to open the stream to, must not be {@code null}.
     * @return The new output stream, never {@code null}.
     * @throws FileNotFoundException If the file could not be opened.
     */
    OutputStream newOutputStream( File output )
        throws FileNotFoundException;

    /**
     * Adds a warning/error message about a problem with the specified file to the build. For a build on the
     * commandline, these messages are usually turned into ordinary log messages send to the console. IDEs however may
     * use different means to signal the problem to the user like error markers shown in the file's editor.<br>
     * <br>
     * The warnings/errors are generally persistent until the underlying problem has been solved. Hence use of this
     * method must be preceded with a call to {@link #clearMessages(File)} when processing an input file in order to
     * reset any previous build state like this:
     * 
     * <pre>
     * buildContextManager.clearMessages( input );
     * ...
     * buildContextManager.addMessage( input, ... );
     * ...
     * buildContextManager.addMessage( input, ... );
     * </pre>
     * 
     * Build is considered "failed" if any messages of severity {@link #SEVERITY_ERROR} exist for files matching the
     * path sets passed to {@link #getInputs(PathSet, boolean)}, either added during the current build or still
     * uncleared from a previous build. Failed builds are handled according to build execution environment. For example,
     * during Maven build MojoExecutionException holding the error message(s) will be raised after mojo execution, or
     * error markers on corresponding input files will be created inside Eclipse IDE.
     * 
     * @param input The input file to add a message for, must not be {@code null}.
     * @param line The one-based line number inside the file where the problem exists, may be non-positive if the line
     *            number is unknown.
     * @param column The one-based column number inside the file where the problem exists, may be non-positive if the
     *            column number is unknown.
     * @param message The warning/error message to add, may be {@code null} to derive a message from the issue cause.
     * @param severity The severity of the problem, one of {@link #SEVERITY_WARNING} or {@link #SEVERITY_ERROR}.
     * @param cause The cause of the problem, may be {@code null} if none.
     * @see #clearMessages(File)
     */
    void addMessage( File input, int line, int column, String message, int severity, Throwable cause );

    /**
     * Clears any previously generated warning/error messages for the specified input file. This method should be called
     * before an input file is processed and any current problems are reported.
     * 
     * @param input The input file whose warning/error messages should be cleared, must not be {@code null}.
     * @see #addMessage(File, int, int, String, int, Throwable)
     */
    void clearMessages( File input );

}
