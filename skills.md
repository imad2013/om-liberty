
Create and Deploy an orders-service on WebSphere Open Liberty (latest) in Kubernetes, expose health/metrics, wire Prometheus/Grafana, and perform a blue/green rollout from v1 → v2.
Components:
Runtime: Liberty container per service.
Kubernetes: Single namespace orders.
Ingress: Simple HTTP ingress (you can swap for gateway later).
Observability: Prometheus scraping /metrics, Grafana dashboards.
Rollout: Two Liberty deployments (orders-blue, orders-green) with traffic switching via Ingress.
---
2. Liberty configuration (`server.xml`)
This server.xml is minimal but production-shaped: health, metrics, JAX-RS, JSON, security off for simplicity (you’d add later).
<server description="Orders Service Liberty Server">

    <!-- Features: Jakarta REST, JSON, CDI, MicroProfile Health & Metrics -->
    <featureManager>
        <feature>jakartaee-10.0</feature>
        <feature>mpHealth-4.0</feature>
        <feature>mpMetrics-5.0</feature>
        <feature>mpConfig-3.0</feature>
    </featureManager>

    <!-- HTTP endpoint -->
    <httpEndpoint id="defaultHttpEndpoint"
                  host="0.0.0.0"
                  httpPort="9080" />

    <!-- Application -->
    <application id="orders-service"
                 location="orders-service.war"
                 type="war" />

    <!-- Logging (stdout-friendly) -->
    <logging consoleLogLevel="INFO"
             traceSpecification="*=info"
             messageFormat="JSON" />

    <!-- MicroProfile Health -->
    <mpHealth id="healthConfig" />

    <!-- MicroProfile Metrics -->
    <mpMetrics id="metricsConfig" />

</server>

You’d package a simple JAX-RS app with:
/orders → main business endpoint.
/health/live, /health/ready → health.
/metrics → Prometheus metrics.
---
3. Dockerfile for Liberty orders-service
Assume you have orders-service.war built via Maven/Gradle.
FROM icr.io/appcafe/websphere-liberty:kernel-java17

# Copy Liberty config and app
COPY server.xml /config/
COPY orders-service.war /config/apps/

# Optional: run Liberty configuration script (feature provisioning, etc.)
RUN configure.sh

# Run as non-root
USER 1001

EXPOSE 9080

CMD ["server", "run", "defaultServer"]

Build and push:
docker build -t my-registry/orders-service:1.0.0 .
docker push my-registry/orders-service:1.0.0

For v2:
docker build -t my-registry/orders-service:2.0.0 .
docker push my-registry/orders-service:2.0.0

---
4. Kubernetes manifests: blue/green deployments
We’ll use:
Service: orders-service (stable virtual IP).
Deployments: orders-blue (v1), orders-green (v2).
Ingress: Routes traffic to either blue or green via annotation or path.
4.1 Namespace
apiVersion: v1
kind: Namespace
metadata:
  name: orders

4.2 Service (stable endpoint)
apiVersion: v1
kind: Service
metadata:
  name: orders-service
  namespace: orders
spec:
  selector:
    app: orders-service
    color: blue   # initial selector points to blue
  ports:
    - name: http
      port: 80
      targetPort: 9080
  type: ClusterIP

4.3 Blue deployment (v1)
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
            - containerPort: 9080
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 9080
          livenessProbe:
            httpGet:
              path: /health/live
              port: 9080
          env:
            - name: MP_METRICS_TAGS
              value: "version=v1,color=blue"

4.4 Green deployment (v2)
Initially deployed but not receiving traffic (Service selector still points to color: blue).
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders-green
  namespace: orders
spec:
  replicas: 3
  selector:
    matchLabels:
      app: orders-service
      color: green
  template:
    metadata:
      labels:
        app: orders-service
        color: green
    spec:
      containers:
        - name: liberty
          image: my-registry/orders-service:2.0.0
          ports:
            - containerPort: 9080
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 9080
          livenessProbe:
            httpGet:
              path: /health/live
              port: 9080
          env:
            - name: MP_METRICS_TAGS
              value: "version=v2,color=green"

4.5 Ingress
Simple HTTP ingress (replace with your controller’s annotations as needed).
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: orders-ingress
  namespace: orders
  annotations:
    kubernetes.io/ingress.class: "nginx"
spec:
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

---
5. Prometheus and Grafana integration
Assuming you’re using Prometheus Operator; if not, you’d configure scrape configs manually.
5.1 ServiceMonitor for orders-service
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

Prometheus will scrape /metrics on the orders-service Service, which routes to whichever color is active.
5.2 Grafana dashboard basics
You’d create a dashboard with panels like:
HTTP requests:
Query example:
sum(rate(http_server_requests_total{app="orders-service"}[5m]))

Latency (p95):
histogram_quantile(0.95,
  sum(rate(http_server_requests_seconds_bucket{app="orders-service"}[5m])) by (le))

Error rate:
sum(rate(http_server_requests_total{app="orders-service",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_total{app="orders-service"}[5m]))

You can filter by version or color using the tags we set via MP_METRICS_TAGS.
---
6. Blue/green rollout procedure
6.1 Initial state
Blue: v1, receiving all traffic (Service selector color: blue).
Green: v2, deployed, passing health checks, but not receiving traffic.
6.2 Validation of green
Direct pod testing:
Port-forward a green pod:
kubectl -n orders port-forward deploy/orders-green 9080:9080
curl http://localhost:9080/orders
curl http://localhost:9080/health/ready

Metrics check:
Prometheus should show metrics for color="green" even if no external traffic (you can generate synthetic load).
6.3 Switch traffic from blue to green
Update the Service selector:
apiVersion: v1
kind: Service
metadata:
  name: orders-service
  namespace: orders
spec:
  selector:
    app: orders-service
    color: green   # changed from blue to green
  ports:
    - name: http
      port: 80
      targetPort: 9080
  type: ClusterIP

Apply:
kubectl apply -f orders-service.yaml

Effect:
Ingress still points to orders-service.
orders-service now routes to pods labeled color=green.
Blue pods remain running but idle (no traffic).
6.4 Post-switch validation
Functional:
curl https://orders.example.com/orders → verify v2 behavior.
Health:
Check /health/ready via ingress.
Metrics:
Grafana should show traffic shifting from version=v1 to version=v2.
6.5 Rollback (if needed)
If v2 misbehaves:
Change Service selector back to color: blue.
Optionally scale down or fix green.
spec:
  selector:
    app: orders-service
    color: blue

Apply and traffic returns to v1 with minimal disruption.
---
7. How to actually run this lab
Minimal steps:
Build app + image:
Implement a simple JAX-RS orders resource + MP Health + MP Metrics.
Build orders-service.war.
Build/push v1 and v2 images.
Create cluster namespace:
kubectl apply -f namespace.yaml
Deploy blue, green, service, ingress:
kubectl apply -f orders-blue.yaml
kubectl apply -f orders-green.yaml
kubectl apply -f orders-service.yaml
kubectl apply -f orders-ingress.yaml
Install Prometheus/Grafana (if not already):
Use Prometheus Operator or Helm charts.
kubectl apply -f orders-servicemonitor.yaml
Test blue/green switch:
Validate blue.
Validate green.
Flip Service selector.
Observe metrics and behavior.
---
If you want, next iteration we can:
Add security hardening to this lab (TLS, JWT, headers), or
Turn this into a slide-ready, 16:9 blueprint with sequence diagrams and rollout timeline you can use in interviews.
