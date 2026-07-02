You’re turning this into a legit platform story—nice. Let’s harden the same lab without drowning it.
---
1. Security goals for the hardened lab
TLS: All external traffic over HTTPS; Liberty speaks HTTPS internally if needed.
JWT auth: API gateway/identity provider issues JWT; Liberty validates via MicroProfile JWT.
Security headers: Enforce baseline browser and API protections.
Runtime hardening: Non-root, minimal features, secrets via Kubernetes.
We’ll layer changes on top of the existing orders-service lab.
---
2. Hardened Liberty `server.xml` (TLS, JWT, headers)
2.1 Updated `server.xml`
<server description="Orders Service Liberty Server (Hardened)">

    <!-- Features: Jakarta REST, JSON, CDI, MicroProfile Health, Metrics, Config, JWT, Security -->
    <featureManager>
        <feature>jakartaee-10.0</feature>
        <feature>mpHealth-4.0</feature>
        <feature>mpMetrics-5.0</feature>
        <feature>mpConfig-3.0</feature>
        <feature>mpJwt-2.1</feature>
        <feature>appSecurity-4.0</feature>
    </featureManager>

    <!-- HTTPS endpoint -->
    <httpEndpoint id="defaultHttpEndpoint"
                  host="0.0.0.0"
                  httpPort="-1"
                  httpsPort="9443"
                  sslRef="defaultSSLConfig" />

    <!-- SSL configuration -->
    <keyStore id="defaultKeyStore"
              location="${env.KEYSTORE_PATH}"
              password="${env.KEYSTORE_PASSWORD}" />

    <ssl id="defaultSSLConfig"
         keyStoreRef="defaultKeyStore"
         sslProtocol="TLSv1.3"
         clientAuthenticationSupported="true"
         clientAuthentication="false" />

    <!-- Application -->
    <application id="orders-service"
                 location="orders-service.war"
                 type="war" />

    <!-- Logging (JSON, stdout-friendly) -->
    <logging consoleLogLevel="INFO"
             traceSpecification="*=info"
             messageFormat="JSON" />

    <!-- MicroProfile Health & Metrics -->
    <mpHealth id="healthConfig" />
    <mpMetrics id="metricsConfig" />

    <!-- MicroProfile JWT configuration -->
    <mpJwt id="jwtConfig"
           issuer="${env.JWT_ISSUER}"
           audiences="${env.JWT_AUDIENCE}"
           jwksUri="${env.JWT_JWKS_URI}" />

    <!-- HTTP security headers via dispatcher -->
    <httpDispatcher
        setHeader="Strict-Transport-Security=max-age=31536000;includeSubDomains;preload;
                   X-Content-Type-Options=nosniff;
                   X-Frame-Options=DENY" />

</server>

Key points:
TLS: HTTP disabled (httpPort="-1"), HTTPS only.
Keystore: Path and password injected via env vars (backed by Kubernetes Secret).
JWT: Liberty validates tokens from your IdP/gateway via JWKS.
Headers: HSTS, X-Content-Type-Options, X-Frame-Options enforced globally.
---
3. JWT-secured JAX-RS resource (orders endpoint)
Inside orders-service.war, you’d secure endpoints with MicroProfile JWT:
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import jakarta.inject.Inject;

@Path("/orders")
public class OrdersResource {

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({"orders.read"})
    public Response getOrders() {
        String user = jwt.getName();
        // business logic...
        return Response.ok("Orders for user: " + user).build();
    }
}

Assumptions:
JWT contains groups or roles that map to orders.read.
Gateway/IdP issues tokens with iss, aud, and keys matching mpJwt config.
---
4. Container and Kubernetes hardening
4.1 Hardened Dockerfile
FROM icr.io/appcafe/websphere-liberty:kernel-java17

# Copy Liberty config and app
COPY server.xml /config/
COPY orders-service.war /config/apps/

RUN configure.sh

# Non-root user
USER 1001

EXPOSE 9443

CMD ["server", "run", "defaultServer"]

4.2 Secrets and env wiring (TLS + JWT)
TLS keystore secret:
apiVersion: v1
kind: Secret
metadata:
  name: orders-tls
  namespace: orders
type: Opaque
data:
  keystore.p12: <base64-encoded-keystore>
  keystore-password: <base64-encoded-password>

JWT config secret:
apiVersion: v1
kind: Secret
metadata:
  name: orders-jwt
  namespace: orders
type: Opaque
data:
  issuer: <base64-encoded-issuer>
  audience: <base64-encoded-audience>
  jwks-uri: <base64-encoded-jwks-uri>

4.3 Updated deployment (blue example, hardened)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders-blue
  namespace: orders
spec:
  replicas: 3
  selector:
    matchLabels:
      app: orders-service
      color: blue
  template:
    metadata:
      labels:
        app: orders-service
        color: blue
    spec:
      containers:
        - name: liberty
          image: my-registry/orders-service:1.0.0
          ports:
            - containerPort: 9443
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 9443
              scheme: HTTPS
          livenessProbe:
            httpGet:
              path: /health/live
              port: 9443
              scheme: HTTPS
          env:
            - name: KEYSTORE_PATH
              value: "/etc/ssl/keystore.p12"
            - name: KEYSTORE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: orders-tls
                  key: keystore-password
            - name: JWT_ISSUER
              valueFrom:
                secretKeyRef:
                  name: orders-jwt
                  key: issuer
            - name: JWT_AUDIENCE
              valueFrom:
                secretKeyRef:
                  name: orders-jwt
                  key: audience
            - name: JWT_JWKS_URI
              valueFrom:
                secretKeyRef:
                  name: orders-jwt
                  key: jwks-uri
            - name: MP_METRICS_TAGS
              value: "version=v1,color=blue"
          volumeMounts:
            - name: tls-keystore
              mountPath: /etc/ssl
              readOnly: true
      volumes:
        - name: tls-keystore
          secret:
            secretName: orders-tls
            items:
              - key: keystore.p12
                path: keystore.p12

Green deployment mirrors this, just with color=green and version=v2.
---
5. Ingress and TLS termination + JWT
You’ve got two main patterns:
Pattern A: TLS termination + JWT at gateway; Liberty sees HTTP but still enforces JWT.
Pattern B: End-to-end TLS; gateway passes HTTPS to Liberty.
Let’s assume Pattern A (common in practice):
5.1 Ingress with TLS
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: orders-ingress
  namespace: orders
  annotations:
    kubernetes.io/ingress.class: "nginx"
    nginx.ingress.kubernetes.io/backend-protocol: "HTTPS"
spec:
  tls:
    - hosts:
        - orders.example.com
      secretName: orders-ingress-tls
  rules:
    - host: orders.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: orders-service
                port:
                  number: 80

JWT handling:
API gateway/ingress (e.g., NGINX + Lua, Kong, Istio) validates JWT and forwards it in Authorization: Bearer <token>.
Liberty validates again via mpJwt (defense in depth) or trusts gateway and uses token for auth.
---
6. Security headers and verification
6.1 Headers from Liberty
We already configured:
<httpDispatcher
    setHeader="Strict-Transport-Security=max-age=31536000;includeSubDomains;preload;
               X-Content-Type-Options=nosniff;
               X-Frame-Options=DENY" />

You can add CSP via app-level filter if you serve HTML:
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        res.getHeaders().add("Content-Security-Policy", "default-src 'self'");
    }
}

6.2 Verify with curl
curl -k -I https://orders.example.com/orders \
  -H "Authorization: Bearer <jwt>"

# Check:
# - 200/401/403 as expected
# - Strict-Transport-Security
# - X-Content-Type-Options
# - X-Frame-Options
# - (optional) Content-Security-Policy

---
7. Observability with security in place
7.1 Prometheus scraping over HTTPS
If Prometheus scrapes /metrics via Service (HTTPS), you may need:
TLS config in Prometheus (if using end-to-end TLS).
Or keep /metrics behind cluster-internal HTTP if you prefer (Liberty can expose metrics on HTTP while app traffic is HTTPS).
Example ServiceMonitor (unchanged if using HTTP internally):
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: orders-service-monitor
  namespace: orders
spec:
  selector:
    matchLabels:
      app: orders-service
  namespaceSelector:
    matchNames:
      - orders
  endpoints:
    - port: http
      path: /metrics
      interval: 15s

7.2 Security-aware metrics
You can expose metrics like:
Auth failures: Count of 401/403.
JWT validation errors: Custom counters in your auth filter.
Example:
@Counted(name = "jwt_validation_failures", description = "JWT validation failures")
public void handleInvalidJwt() {
    // increment when JWT invalid
}

---
8. Blue/green with security: what changes operationally
Blue vs. green: Both versions must have valid TLS and JWT config; you can test green with real tokens before switching.
Switch: Same Service selector flip (color=blue → color=green).
Validation:
Functional: /orders with JWT.
Security: headers present, TLS correct, JWT enforced.
Observability: metrics tagged by version and color.
---
If you want, next step we can:
Add a concise, interview-ready checklist summarizing “Liberty microservice hardening” you can rattle off in a loop, or
Design a slide-ready sequence: client → gateway (JWT/TLS) → Liberty → Prometheus/Grafana, with blue/green timeline.
