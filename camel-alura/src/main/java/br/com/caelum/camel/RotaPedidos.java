package br.com.caelum.camel;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultCamelContext;

import com.thoughtworks.xstream.XStream;

public class RotaPedidos {

	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		context.addComponent("activemq", ActiveMQComponent.activeMQComponent("tcp://localhost:61616"));
		
		final XStream xstream = new XStream();
		xstream.alias("negociacao", Negociacao.class);
		
		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				
				/**from("timer://negociacoes?fixedRate=true&delay=1s&period=360s").
			      to("http4://argentumws-spring.herokuapp.com/negociacoes").
			          convertBodyTo(String.class).
			          unmarshal(new XStreamDataFormat(xstream)).
			          split(body()).
			          log("${body}").
			          end();
			          setHeader(Exchange.FILE_NAME, constant("negociacoes.xml")).
			    to("file:saida");*/
				
				errorHandler(deadLetterChannel("activemq:queue:pedidos.DLQ").
						logExhaustedMessageHistory(true).
							maximumRedeliveries(3).
								redeliveryDelay(2000).
									onRedelivery(new Processor() {

										@Override
										public void process(Exchange exchange) throws Exception {
											int counter = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER);
											int max = (int) exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER);
											System.out.println("Redelivery "+counter+"/"+max);
										}
										
									}));
				
				//from("file:pedidos?delay=5s&noop=true").				
				from("activemq:queue:pedidos").
				routeId("rota-pedidos").
				to("validator:pedido.xsd").
				log("chegamos aqui").
				/**to("seda:soap").
				//to("seda:http");;*/
				//espalhar o msm body nas 2 chamadas
				multicast().
				parallelProcessing().
	            	timeout(500).
						to("direct:soap").
						to("direct:http");
				
				
				from("direct:http").
				routeId("rota-http").
				setProperty("pedidoId", xpath("/pedido/id/text()")).
					setProperty("clienteId", xpath("/pedido/pagamento/email-titular/text()")).
					split().
						xpath("/pedido/itens/item").
					filter().
						xpath("/item/formato[text()='EBOOK']").
					setProperty("ebookId", xpath("/item/livro/codigo/text()")).
					marshal().xmljson().
					log("${id} - ${body}").
					//setHeader(Exchange.HTTP_METHOD, HttpMethods.POST).
					setHeader(Exchange.HTTP_METHOD, HttpMethods.GET).
					setHeader(Exchange.HTTP_QUERY, simple("clienteId=${property.clienteId}&pedidoId=${property.pedidoId}&ebookId=${property.ebookId}")).
				to("http4://localhost:8080/webservices/ebook/item");
			
				from("direct:soap").
					routeId("rota-soap").
					to("xslt:pedido-para-soap.xslt").
						//setBody(constant("<envelope>teste</envelope>")).
					log("${body}").
					setHeader(Exchange.CONTENT_TYPE, constant("text/xml")).
				to("http4://localhost:8080/webservices/financeiro");
			}
			
		});
		
		

		context.start();
		Thread.sleep(20000);
		context.stop();
	}	
}
