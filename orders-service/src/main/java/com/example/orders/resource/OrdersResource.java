package com.example.orders.resource;

import com.example.orders.model.CreateOrderRequest;
import com.example.orders.model.Order;
import com.example.orders.service.OrderService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrdersResource {

    @Inject
    OrderService orderService;

    @Context
    SecurityContext securityContext;

    @GET
    @RolesAllowed({"orders.read"})
    @Timed(name = "orders_list_duration", description = "Time to list orders")
    @Counted(name = "orders_list_total", description = "Number of order list requests")
    public Response getOrders() {
        List<Order> orders = orderService.listOrders();
        Map<String, Object> body = new HashMap<>();
        String user = securityContext.getUserPrincipal() != null
                ? securityContext.getUserPrincipal().getName()
                : "anonymous";
        body.put("user", user);
        body.put("version", orderService.getServiceVersion());
        body.put("orders", orders);
        return Response.ok(body).build();
    }

    @POST
    @RolesAllowed({"orders.write"})
    @Timed(name = "orders_create_duration", description = "Time to create an order")
    @Counted(name = "orders_create_total", description = "Number of order create requests")
    public Response createOrder(CreateOrderRequest request) {
        if (request.getCustomerName() == null || request.getProduct() == null || request.getQuantity() <= 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "customerName, product, and positive quantity are required"))
                    .build();
        }
        Order order = orderService.createOrder(request);
        return Response.status(Response.Status.CREATED).entity(order).build();
    }

    @PATCH
    @Path("/{id}/fulfill")
    @RolesAllowed({"orders.write"})
    @Timed(name = "orders_fulfill_duration", description = "Time to fulfill an order")
    @Counted(name = "orders_fulfill_total", description = "Number of order fulfill requests")
    public Response fulfillOrder(@PathParam("id") long id) {
        Order order = orderService.fulfillOrder(id);
        return Response.ok(order).build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({"orders.write"})
    @Timed(name = "orders_delete_duration", description = "Time to delete an order")
    @Counted(name = "orders_delete_total", description = "Number of order delete requests")
    public Response deleteOrder(@PathParam("id") long id) {
        orderService.deleteOrder(id);
        return Response.noContent().build();
    }

    @GET
    @Path("/version")
    @PermitAll
    public Response getVersion() {
        return Response.ok(Map.of("version", orderService.getServiceVersion())).build();
    }
}
