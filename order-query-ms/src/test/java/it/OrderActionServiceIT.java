package it;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.Assert;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ibm.labs.kc.order.query.action.OrderAction;
import ibm.labs.kc.order.query.action.OrderActionInfo;
import ibm.labs.kc.order.query.kafka.ApplicationConfig;
import ibm.labs.kc.order.query.model.Address;
import ibm.labs.kc.order.query.model.Container;
import ibm.labs.kc.order.query.model.ContainerAssignment;
import ibm.labs.kc.order.query.model.Order;
import ibm.labs.kc.order.query.model.Rejection;
import ibm.labs.kc.order.query.model.VoyageAssignment;
import ibm.labs.kc.order.query.model.events.AssignContainerEvent;
import ibm.labs.kc.order.query.model.events.AssignOrderEvent;
import ibm.labs.kc.order.query.model.events.ContainerDeliveredEvent;
import ibm.labs.kc.order.query.model.events.ContainerEvent;
import ibm.labs.kc.order.query.model.events.ContainerGoodsLoadedEvent;
import ibm.labs.kc.order.query.model.events.ContainerGoodsUnLoadedEvent;
import ibm.labs.kc.order.query.model.events.ContainerOffMaintainanceEvent;
import ibm.labs.kc.order.query.model.events.ContainerOffShipEvent;
import ibm.labs.kc.order.query.model.events.ContainerOffTruckEvent;
import ibm.labs.kc.order.query.model.events.ContainerOnMaintainanceEvent;
import ibm.labs.kc.order.query.model.events.ContainerOnShipEvent;
import ibm.labs.kc.order.query.model.events.ContainerOnTruckEvent;
import ibm.labs.kc.order.query.model.events.ContainerOrderAssignedEvent;
import ibm.labs.kc.order.query.model.events.ContainerOrderReleasedEvent;
import ibm.labs.kc.order.query.model.events.ContainerRemovedEvent;
import ibm.labs.kc.order.query.model.events.ContainerAddedEvent;
import ibm.labs.kc.order.query.model.events.ContainerAtLocationEvent;
import ibm.labs.kc.order.query.model.events.CreateOrderEvent;
import ibm.labs.kc.order.query.model.events.OrderCompletedEvent;
import ibm.labs.kc.order.query.model.events.OrderEvent;
import ibm.labs.kc.order.query.model.events.RejectOrderEvent;

public class OrderActionServiceIT {

	private String port = System.getProperty("liberty.test.port");
    private String endpoint = "/orders/";
    private String url = "http://localhost:" + port + endpoint;

    @Test
    public void testOrderStatusNoAvailability() throws Exception {

    	String orderID = UUID.randomUUID().toString();

    	Address addr = new Address("myStreet", "myCity", "myCountry", "myState", "myZipcode");
    	Order order = new Order(orderID, "productId", "custId", 2,
    	                addr, "2019-02-10T13:30Z",
    	                addr, "2019-02-10T13:30Z", Order.PENDING_STATUS);
    	OrderEvent ord_event = new CreateOrderEvent(System.currentTimeMillis(), "1", order);
    	sendEvent("testOrderStatusNoAvailability", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event));

    	Rejection rejection = new Rejection(orderID, "custId");
    	OrderEvent ord_event2 = new RejectOrderEvent(System.currentTimeMillis(), "1", rejection);
    	sendEvent("testOrderStatusNoAvailability", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event2));

    	OrderActionInfo expectedOrder = OrderActionInfo.newFromOrder(order);
    	expectedOrder.reject(rejection);
    	OrderAction expectedComplexQueryOrder = OrderAction.newFromHistoryOrder(expectedOrder, ord_event2.getTimestampMillis(), ord_event2.getType());

        Thread.sleep(10000L);

        int maxattempts = 10;

        for(int i=0; i<maxattempts; i++) {
          Response response = makeGetRequest(url + "orderHistory/"+orderID);
          Assert.assertEquals(response.getStatus(), 200);
          String responseString = response.readEntity(String.class);
          ArrayList<OrderAction> complexQueryOrders = new Gson().fromJson(responseString,new TypeToken<List<OrderAction>>(){}.getType());
          Assert.assertTrue(complexQueryOrders.contains(expectedComplexQueryOrder));
        }

    }

    @Test
    public void testOrderStatus() throws Exception {

    	String orderID = UUID.randomUUID().toString();
    	String containerID = UUID.randomUUID().toString();

    	Address addr = new Address("myStreet", "myCity", "myCountry", "myState", "myZipcode");
        Order order = new Order(orderID, "productId", "custId", 2,
                addr, "2019-01-10T13:30Z",
                addr, "2019-01-10T13:30Z", Order.PENDING_STATUS);
        OrderEvent ord_event = new CreateOrderEvent(System.currentTimeMillis(), "1", order);
        sendEvent("testOrderStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event));

        OrderActionInfo expectedOrder = OrderActionInfo.newFromOrder(order);

        VoyageAssignment va = new VoyageAssignment(orderID, "myVoyage");
        OrderEvent ord_event2 = new AssignOrderEvent(System.currentTimeMillis(), "1", va);
        sendEvent("testOrderStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event2));

        expectedOrder.assign(va);

        ContainerAssignment container = new ContainerAssignment(orderID, containerID);
        OrderEvent ord_event3 = new AssignContainerEvent(System.currentTimeMillis(), "1", container);
        sendEvent("testOrderStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event3));

        expectedOrder.assignContainer(container);

        Container cont = new Container(containerID, "brand", "type", 1, 1, 1, "ContainerAdded");
        ContainerEvent cont_event = new ContainerAddedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event));

        OrderActionInfo expectedContainer = OrderActionInfo.newFromContainer(cont);

        cont.setStatus("ContainerAtLocation");
        ContainerEvent cont_event2 = new ContainerAtLocationEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event2));

        expectedContainer.containerAtLocation(cont);

        cont.setStatus("ContainerOnMaintenance");
        ContainerEvent cont_event3 = new ContainerOnMaintainanceEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event3));

        expectedContainer.containerOnMaintainance(cont);
        
        cont.setStatus("ContainerOffMaintenance");
        ContainerEvent cont_event4 = new ContainerOffMaintainanceEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event4));

        expectedContainer.containerOffMaintainance(cont);
        
        cont.setStatus("ContainerAssignedToOrder");
        ContainerEvent cont_event5 = new ContainerOrderAssignedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event5));

        expectedContainer.containerOrderAssignment(cont);
        
        cont.setStatus("ContainerReleasedFromOrder");
        ContainerEvent cont_event6 = new ContainerOrderReleasedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event6));

        expectedContainer.containerOrderAssignment(cont);

        cont.setStatus("ContainerGoodLoaded");
        ContainerEvent cont_event7 = new ContainerGoodsLoadedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event7));

        expectedContainer.containerGoodsLoaded(cont);
        
        cont.setStatus("ContainerOnShip");
        ContainerEvent cont_event8 = new ContainerOnShipEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event8));

        expectedContainer.containerOnShip(cont);
        
        cont.setStatus("ContainerOffShip");
        ContainerEvent cont_event9 = new ContainerOffShipEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event9));

        expectedContainer.containerOffShip(cont);
        
        cont.setStatus("ContainerGoodUnLoaded");
        ContainerEvent cont_event10 = new ContainerGoodsUnLoadedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event10));

        expectedContainer.containerGoodsUnloaded(cont);
        
        cont.setStatus("ContainerOnTruck");
        ContainerEvent cont_event11 = new ContainerOnTruckEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event11));

        expectedContainer.containerOnTruck(cont);
        
        cont.setStatus("ContainerOffTruck");
        ContainerEvent cont_event12 = new ContainerOffTruckEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event12));

        expectedContainer.containerOffTruck(cont);

        OrderEvent ord_event6 = new ContainerDeliveredEvent(System.currentTimeMillis(), "1", container);
        sendEvent("testOrderStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event6));

        expectedOrder.containerDelivered(container);

        Order order1 = new Order(orderID);
        OrderEvent ord_event7 = new OrderCompletedEvent(System.currentTimeMillis(), "1", order1);
        sendEvent("testOrderStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event7));

        expectedOrder.orderCompleted(order1);

    	OrderAction expectedComplexQueryOrder = OrderAction.newFromHistoryOrder(expectedOrder, ord_event7.getTimestampMillis(), ord_event7.getType());
        
    	int maxattempts = 10;

        for(int i=0; i<maxattempts; i++) {
          Response response = makeGetRequest(url + "orderHistory/"+orderID);
          Assert.assertEquals(response.getStatus(), 200);
          String responseString = response.readEntity(String.class);
          ArrayList<OrderAction> complexQueryOrders = new Gson().fromJson(responseString,new TypeToken<List<OrderAction>>(){}.getType());
          Assert.assertTrue(complexQueryOrders.contains(expectedComplexQueryOrder));
        }
    }
    
    @Test
    public void testOrderRemovedContainerStatus() throws Exception {

    	String orderID = UUID.randomUUID().toString();
    	String containerID = UUID.randomUUID().toString();
    	
    	Container cont = new Container(containerID, "brand", "type", 1, 1, 1, "ContainerAdded");
        ContainerEvent cont_event = new ContainerAddedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event));

        OrderActionInfo expectedContainer = OrderActionInfo.newFromContainer(cont);

    	Address addr = new Address("myStreet", "myCity", "myCountry", "myState", "myZipcode");
        Order order = new Order(orderID, "productId", "custId", 2,
                addr, "2019-01-10T13:30Z",
                addr, "2019-01-10T13:30Z", Order.PENDING_STATUS);
        OrderEvent ord_event = new CreateOrderEvent(System.currentTimeMillis(), "1", order);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event));

        OrderActionInfo expectedOrder = OrderActionInfo.newFromOrder(order);

        VoyageAssignment va = new VoyageAssignment(orderID, "myVoyage");
        OrderEvent ord_event2 = new AssignOrderEvent(System.currentTimeMillis(), "1", va);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event2));

        expectedOrder.assign(va);

        ContainerAssignment container = new ContainerAssignment(orderID, containerID);
        OrderEvent ord_event3 = new AssignContainerEvent(System.currentTimeMillis(), "1", container);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.ORDER_TOPIC, orderID, new Gson().toJson(ord_event3));

        expectedOrder.assignContainer(container);

        cont.setStatus("ContainerAtLocation");
        ContainerEvent cont_event2 = new ContainerAtLocationEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event2));

        expectedContainer.containerAtLocation(cont);

        cont.setStatus("ContainerOnMaintenance");
        ContainerEvent cont_event3 = new ContainerOnMaintainanceEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event3));

        expectedContainer.containerOnMaintainance(cont);
        
        cont.setStatus("ContainerRemoved");
        ContainerEvent cont_event4 = new ContainerRemovedEvent(System.currentTimeMillis(), "1", cont);
        sendEvent("testOrderRemovedContainerStatus", ApplicationConfig.CONTAINER_TOPIC, containerID, new Gson().toJson(cont_event4));

        expectedContainer.containerRemoved(cont);

    	OrderAction expectedComplexQueryOrder = OrderAction.newFromHistoryContainer(expectedContainer, cont_event4.getTimestampMillis(), cont_event4.getType());

        int maxattempts = 10;
        
        Thread.sleep(5000L);

        for(int i=0; i<maxattempts; i++) {
          Response response = makeGetRequest(url + "orderHistory/"+orderID);
          Assert.assertEquals(response.getStatus(), 200);
          String responseString = response.readEntity(String.class);
          ArrayList<OrderAction> complexQueryOrders = new Gson().fromJson(responseString,new TypeToken<List<OrderAction>>(){}.getType());
          Assert.assertTrue(complexQueryOrders.contains(expectedComplexQueryOrder));
        }
    }

    protected Response makeGetRequest(String url) {
        Client client = ClientBuilder.newClient();
        Invocation.Builder invoBuild = client.target(url).request();
        Response response = invoBuild.get();
        return response;
    }

    private void sendEvent(String clientID, String topic, String key, String value) throws Exception {
        Properties properties = ApplicationConfig.getProducerProperties(clientID);

        try(Producer<String, String> producer = new KafkaProducer<>(properties)) {

            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);

            Future<RecordMetadata> future = producer.send(record);
            future.get(10000L, TimeUnit.MILLISECONDS);
        }
    }

}
