package it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;

import org.junit.Test;

import com.google.gson.Gson;

import ibm.labs.kc.order.command.dto.CreateOrderRequest;
import ibm.labs.kc.order.command.model.Order;

public class CreateOrderEndpointIT {

    private String port = System.getProperty("liberty.test.port");
    private String endpoint = "/orders";
    private String url = "http://localhost:" + port + endpoint;

    @Test
    public void testCreateSuccess() throws Exception {
        System.out.println("Testing endpoint " + url);

        CreateOrderRequest cor = new CreateOrderRequest();
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        cor.setProductId("myProductID");
        cor.setQuantity(100);

        Response response = makePostRequest(url, new Gson().toJson(cor));
        try {

            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, responseCode, 200);
            assertTrue(response.hasEntity());
            String responseString = response.readEntity(String.class);

            Order o = new Gson().fromJson(responseString, Order.class);
            assertNotNull(o.getOrderId());
            assertEquals(cor.getProductId(), o.getProductId());
            assertEquals(cor.getQuantity(), o.getQuantity());
            assertEquals(cor.getExpectedDeliveryDate(), o.getExpectedDeliveryDate());
            assertEquals("created", o.getStatus());
        } finally {
            response.close();
        }
    }

    @Test
    public void testCreateEmptyJson() throws Exception {
        Response response = makePostRequest(url, "");
        try {
            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, responseCode, 400);
        } finally {
            response.close();
        }
    }

    @Test
    public void testCreateBadOrder() throws Exception {
        CreateOrderRequest cor = new CreateOrderRequest();
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        cor.setProductId("myProductID");
        cor.setQuantity(-100);

        Response response = makePostRequest(url, new Gson().toJson(cor));
        try {
            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, responseCode, 400);
        } finally {
            response.close();
        }
    }

    protected int makeGetRequest(String url) {
        Client client = ClientBuilder.newClient();
        Invocation.Builder invoBuild = client.target(url).request();
        Response response = invoBuild.get();
        int responseCode = response.getStatus();
        response.close();
        return responseCode;
    }

    protected Response makePostRequest(String url, String json) {
        Client client = ClientBuilder.newClient();
        Invocation.Builder invoBuild = client.target(url).request();
        Response response = invoBuild.post(Entity.json(json));
        return response;
    }
}