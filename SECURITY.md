# ChronosQ Security Policy

## Supported versions

ChronosQ is currently under active development.

| Version | Supported |
|---|---|
| Latest `main` branch | Yes |
| Older commits and releases | No |

Security fixes are applied to the latest version of the project.

## Reporting a vulnerability

Please do not create a public GitHub issue for a suspected security
vulnerability.

Report vulnerabilities privately using the repository's GitHub
Security Advisory feature:

1. Open the ChronosQ GitHub repository.
2. Select **Security**.
3. Select **Advisories**.
4. Select **Report a vulnerability**.
5. Include the affected component, reproduction steps and possible impact.

Do not include real passwords, access tokens, personal information or
production data in the report.

## Sensitive information

The following information must never be committed to Git:

- Database passwords
- API keys
- Authentication tokens
- Private certificates
- Webhook secrets
- Production `.env` files
- Customer or production job payloads

Use environment variables or a dedicated secrets manager for
production secrets.

The committed `.env.example` file must contain example values only.

## Database security

Production PostgreSQL should:

- Be accessible only through a private network
- Require a strong password
- Not expose port `5432` publicly
- Use encrypted connections when hosted outside the Docker network
- Be backed up regularly
- Use a database account limited to the permissions ChronosQ needs

## Webhook security

Webhook jobs perform outbound HTTP requests and must be treated as
untrusted input.

Production deployments should:

- Allow only `HTTP` and `HTTPS`
- Prefer `HTTPS`
- Restrict allowed HTTP methods
- Limit URL, header and body sizes
- Reject malformed header names and values
- Apply connection and response timeouts
- Limit response sizes
- Avoid logging authorization headers or sensitive payloads
- Block loopback, private-network, link-local and cloud metadata
  addresses unless explicitly allowed
- Revalidate redirect targets before following redirects

These controls help prevent Server-Side Request Forgery (SSRF).

## API exposure

ChronosQ management and job APIs should not be exposed directly to the
public internet without authentication and network-level protection.

Recommended production controls include:

- TLS termination through a trusted reverse proxy
- Authentication for job-management endpoints
- Authorization based on least privilege
- Request-size limits
- Rate limiting
- Audit logging
- Restricted access to Actuator endpoints

## Container security

The ChronosQ container:

- Runs as a non-root user
- Contains only the runtime and application JAR
- Receives secrets through environment configuration
- Should use pinned and regularly updated base images
- Should be scanned for known vulnerabilities before deployment

## Logging

Logs must not contain:

- Passwords
- Authorization headers
- API tokens
- Database connection secrets
- Complete sensitive job payloads

Job IDs, execution IDs and worker IDs may be logged for operational
troubleshooting.

## Dependency security

Maven, Docker and GitHub Actions dependencies are monitored through
Dependabot.

Every dependency update must pass the ChronosQ CI test suite before it
is merged.

## Security limitations

ChronosQ is an educational and portfolio project until its deployment
has completed:

- Authentication and authorization review
- Webhook SSRF testing
- Dependency and container scanning
- Load and concurrency testing
- Backup and restoration testing
- External security review

Passing automated tests does not by itself guarantee that a deployment
is secure.