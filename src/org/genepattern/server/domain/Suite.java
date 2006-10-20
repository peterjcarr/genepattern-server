/* Auto generated file */

package org.genepattern.server.domain;
import java.util.*;

/**
 * A hibernate mapped POJO representing a Suite.  This class is a near copy of SuiteInfo.  
 * Both are kept for an interim period as we transition to Hibernate.
 * @author jrobinso
 *
 */
public class Suite {

    private String lsid;
    private String name;
    private String author;
    private String owner;
    private String description;
    private Integer accessId;
    private List<String> modules;

    public String getLsid() {
        return this.lsid;
    }

    public void setLsid(String value) {
        this.lsid = value;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getAuthor() {
        return this.author;
    }

    public void setAuthor(String value) {
        this.author = value;
    }

    public String getOwner() {
        return this.owner;
    }

    public void setOwner(String value) {
        this.owner = value;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String value) {
        this.description = value;
    }

    public Integer getAccessId() {
        return this.accessId;
    }

    public void setAccessId(Integer value) {
        this.accessId = value;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

}
