package ch.bzz.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An accounting project (e.g. one club). The project name is used as login name.
 */
@Entity
@Table(name = "project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @Column(name = "project_name", nullable = false, length = 100)
    private String projectName;

    /** BCrypt hash, never the plain password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    public Project(String projectName, String passwordHash) {
        this.projectName = projectName;
        this.passwordHash = passwordHash;
    }
}
