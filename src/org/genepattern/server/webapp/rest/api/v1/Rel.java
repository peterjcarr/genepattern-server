package org.genepattern.server.webapp.rest.api.v1;

/**
 * Enumeration of link relations, values for the 'rel' property in
 * JSON Link representations.
 * 
 * See: http://www.iana.org/assignments/link-relations/link-relations.xhtml
 * 
 * @author pcarr
 */
public enum Rel {
    first,
    last,
    next,
    prev,
    self,
    related
}