package org.eclipse.tesla.incremental.maven.internal;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.Committable;
import org.apache.maven.execution.scope.Disposable;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.tesla.incremental.BuildContext;
import org.eclipse.tesla.incremental.PathSet;
import org.eclipse.tesla.incremental.internal.BuildException;
import org.eclipse.tesla.incremental.internal.DefaultBuildContext;
import org.eclipse.tesla.incremental.internal.DefaultBuildContextManager;
import org.eclipse.tesla.incremental.internal.Digester;
import org.slf4j.Logger;

/**
 * Maven specific BuildContext implementation that provides
 * <ul>
 * <li>Conventional location of incremental build state under ${build.build.directory}/incremental. In the future, this
 * may become configurable via well-known project property.</li>
 * <li>Automatic detection of configuration changes based on
 * <ul>
 * <li>Maven plugin artifacts GAVs, file sizes and timestamps</li>
 * <li>Project effective pom.xml. In the future, this may be narrowed down.</li>
 * <li>Maven session execution, i.e. user and system, properties.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @TODO decide how to handle volatile properties like ${maven.build.timestamp}. Should we always ignore them? Are there
 *       cases where output has to be always regenerated just to include new build timestamp, for example?
 */
@Named
@MojoExecutionScoped
public class MavenBuildContextManager
    implements BuildContext, Committable, Disposable
{

    private DefaultBuildContext context;

    @Inject
    public MavenBuildContextManager( DefaultBuildContextManager manager, MavenSession session, MavenProject project,
                                     MojoExecution execution, Logger logger )
    {
        File outputDirectory = project.getBasedir(); // @TODO really need to get rid of this!

        File stateDirectory = new File( project.getBuild().getDirectory(), "incremental" );

        String builderId = execution.getMojoDescriptor().getId() + ":" + execution.getExecutionId();

        DefaultBuildContext context = manager.newContext( outputDirectory, stateDirectory, builderId );

        Digester digester = context.newDigester();

        // plugin artifacts define behaviour, rebuild whenever behaviour changes
        for ( Artifact artifact : execution.getMojoDescriptor().getPluginDescriptor().getArtifacts() )
        {
            digester.strings( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );
            digester.file( artifact.getFile() );
        }

        // effective pom.xml defines project configuration, rebuild whenever project configuration changes
        // we can't be more specific here because mojo can access entire project model, not just its own configuration
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try
        {
            new MavenXpp3Writer().write( buf, project.getModel() );
        }
        catch ( IOException e )
        {
            // can't happen
        }
        digester.bytes( buf.toByteArray() );

        // execution properties define build parameters passed in from command line and jvm used
        SortedMap<Object, Object> executionProperties = new TreeMap<Object, Object>( session.getExecutionProperties() );
        for ( Map.Entry<Object, Object> property : executionProperties.entrySet() )
        {
            String key = property.getKey().toString();

            // Environment has PID of java process (env.JAVA_MAIN_CLASS_<PID>), SSH_AGENT_PID, unique TMPDIR (on OSX)
            // and other volatile variables.
            if ( !key.startsWith( "env." ) )
            {
                digester.strings( key, property.getValue().toString() );
            }
        }

        boolean full = context.setConfiguration( digester.finish() );

        if ( logger.isDebugEnabled() )
        {
            logger.debug( "New " + ( full ? "full" : "incremental" ) + " BuildContext for " + execution.toString() );
        }

        this.context = context;
    }

    public Serializable getValue( Serializable key )
    {
        return context.getValue( key );
    }

    public <T extends Serializable> T getValue( Serializable key, Class<T> valueType )
    {
        return context.getValue( key, valueType );
    }

    public void setValue( Serializable key, Serializable value )
    {
        context.setValue( key, value );
    }

    public Collection<String> getInputs( PathSet paths )
    {
        return context.getInputs( paths );
    }

    public void addOutput( File input, File output )
    {
        context.addOutput( input, output );
    }

    public void addOutputs( File input, File... outputs )
    {
        context.addOutputs( input, outputs );
    }

    public void addOutputs( File input, Collection<File> outputs )
    {
        context.addOutputs( input, outputs );
    }

    public void addOutputs( File input, PathSet outputs )
    {
        context.addOutputs( input, outputs );
    }

    public void addReferencedInputs( File input, Collection<File> referencedInputs )
    {
        context.addReferencedInputs( input, referencedInputs );
    }

    public OutputStream newOutputStream( File output )
        throws FileNotFoundException
    {
        return context.newOutputStream( output );
    }

    public void addMessage( File input, int line, int column, String message, int severity, Throwable cause )
    {
        context.addMessage( input, line, column, message, severity, cause );
    }

    public void clearMessages( File input )
    {
        context.clearMessages( input );
    }

    public void dispose()
    {
        context.close();
    }

    public void commit()
        throws MojoExecutionException
    {
        try
        {
            context.commit();
        }
        catch ( BuildException e )
        {
            throw new MojoExecutionException( e.getMessage(), e.getCause() );
        }
    }

}
