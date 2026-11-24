package ch.bzz.controller;

import ch.bzz.config.JwtAuthFilter;
import ch.bzz.model.Project;
import ch.bzz.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the project of the logged-in user from the JWT subject
 * that {@link JwtAuthFilter} stored on the request.
 */
@Component
public class CurrentProject {

    private final ProjectRepository projectRepository;

    public CurrentProject(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project get() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        Object name = request.getAttribute(JwtAuthFilter.PROJECT_ATTRIBUTE);
        if (!(name instanceof String projectName)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // The token can outlive a project, so check that it still exists.
        return projectRepository.findById(projectName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
