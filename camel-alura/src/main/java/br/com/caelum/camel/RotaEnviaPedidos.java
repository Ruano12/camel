package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;

import com.thoughtworks.xstream.XStream;

public class RotaEnviaPedidos {
	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));
		
		final XStream xstream = new XStream();
		xstream.alias("negociacao", Negociacao.class);
		
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				
				from("file:pedidos?delay=5s&noop=true").
				to("activemq:queue:pedidos");
			}
			
		});
		
		

		context.start();
		Thread.sleep(20000);
		context.stop();
	}	
}
