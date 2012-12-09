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
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.tesla.incremental.BuildContext;

/**
 * A simple component that demonstrates the use of the incremental build support when the active build context is not
 * directly accessible due to API restrictions.
 */
@Named
public class OutputGenerator
{
    private final BuildContext buildContext;

    @Inject
    public OutputGenerator( BuildContext buildContext )
    {
        this.buildContext = buildContext;
    }

    public void generate( File inputFile, File outputFile, Properties filterProps )
    {
        // register output files
        buildContext.addOutput( inputFile, outputFile );

        // generate output files
        try
        {
            buildContext.clearMessages( inputFile );
            IOUtils.filter( inputFile, buildContext.newOutputStream( outputFile ), "UTF-8", filterProps );
        }
        catch ( IOException e )
        {
            buildContext.addMessage( inputFile, 0, 0, "Could not read file", BuildContext.SEVERITY_ERROR, e );
        }
    }
}
