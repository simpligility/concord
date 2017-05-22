package com.walmartlabs.concord.server.api.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateProjectRequest implements Serializable {

    private final String description;
    private final Set<String> templates;
    private final Map<String, UpdateRepositoryRequest> repositories;
    private final Map<String, Object> cfg;

    public UpdateProjectRequest(Set<String> templates) {
        this(null, templates, null, null);
    }

    public UpdateProjectRequest(Set<String> templates, Map<String, UpdateRepositoryRequest> repositories) {
        this(null, templates, repositories, null);
    }

    @JsonCreator
    public UpdateProjectRequest(@JsonProperty("description") String description,
                                @JsonProperty("templates") Set<String> templates,
                                @JsonProperty("repositories") Map<String, UpdateRepositoryRequest> repositories,
                                @JsonProperty("cfg") Map<String, Object> cfg) {

        this.description = description;
        this.templates = templates;
        this.repositories = repositories;
        this.cfg = cfg;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getTemplates() {
        return templates;
    }

    public Map<String, UpdateRepositoryRequest> getRepositories() {
        return repositories;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public String toString() {
        return "UpdateProjectRequest{" +
                "description='" + description + '\'' +
                ", templates=" + templates +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                '}';
    }
}
