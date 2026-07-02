package com.example.orders.filter;

import jakarta.annotation.Priority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import com.example.orders.config.OrdersConfig;
import jakarta.inject.Inject;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class DevSecurityContextFilter implements ContainerRequestFilter {

    private static final Set<String> DEV_ROLES = Set.of("orders.read", "orders.write");

    @Inject
    OrdersConfig ordersConfig;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (ordersConfig.securityEnabled()) {
            return;
        }
        if (!requiresRoles()) {
            return;
        }
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return () -> "dev-user";
            }

            @Override
            public boolean isUserInRole(String role) {
                return DEV_ROLES.contains(role);
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return SecurityContext.BASIC_AUTH;
            }
        });
    }

    private boolean requiresRoles() {
        if (resourceInfo.getResourceMethod() == null) {
            return false;
        }
        RolesAllowed rolesAllowed = resourceInfo.getResourceMethod().getAnnotation(RolesAllowed.class);
        if (rolesAllowed == null && resourceInfo.getResourceClass() != null) {
            rolesAllowed = resourceInfo.getResourceClass().getAnnotation(RolesAllowed.class);
        }
        return rolesAllowed != null && Arrays.stream(rolesAllowed.value()).findAny().isPresent();
    }
}
