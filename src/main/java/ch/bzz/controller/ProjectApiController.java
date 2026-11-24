package ch.bzz.controller;

import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.model.Project;
import ch.bzz.repository.ProjectRepository;
import ch.bzz.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
public class ProjectApiController implements ProjectApi {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72; // BCrypt only uses the first 72 bytes

    private final ProjectRepository projectRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;
    /** Used to make a login with an unknown project take as long as one with a wrong password. */
    private final String dummyHash = encoder.encode("dummy-password-for-timing");

    public ProjectApiController(ProjectRepository projectRepository, JwtUtil jwtUtil) {
        this.projectRepository = projectRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public ResponseEntity<Void> createProject(LoginRequest loginRequest) {
        String name = loginRequest.getProjectName() == null ? "" : loginRequest.getProjectName().trim();
        String password = loginRequest.getPassword() == null ? "" : loginRequest.getPassword();

        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            log.info("Project creation rejected: invalid input");
            return ResponseEntity.badRequest().build();
        }
        if (projectRepository.existsById(name)) {
            log.info("Project creation rejected: project '{}' already exists", name);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        projectRepository.save(new Project(name, encoder.encode(password)));
        log.info("Project '{}' created", name);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<LoginProject200Response> loginProject(LoginRequest loginRequest) {
        String name = loginRequest.getProjectName() == null ? "" : loginRequest.getProjectName().trim();
        String password = loginRequest.getPassword() == null ? "" : loginRequest.getPassword();

        Optional<Project> project = projectRepository.findById(name);
        String hash = project.map(Project::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = encoder.matches(password, hash);

        if (project.isEmpty() || !passwordMatches) {
            log.info("Login failed for project '{}'", name);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LoginProject200Response response = new LoginProject200Response();
        response.setAccessToken(jwtUtil.generateToken(name));
        log.info("Login successful for project '{}'", name);
        return ResponseEntity.ok(response);
    }
}
