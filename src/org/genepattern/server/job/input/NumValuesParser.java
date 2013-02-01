package org.genepattern.server.job.input;

/**
 * Parse the 'numValues' property from the manifest file for a module.
 * 
 * @author pcarr
 */
public interface NumValuesParser {
    NumValues parseNumValues(String numValues) throws Exception;
}

