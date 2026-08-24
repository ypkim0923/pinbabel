package com.ypkim.pinbabel.influenceranalysis.adapter.in.a2a;

import com.embabel.agent.a2a.server.AgentCardHandler;
import com.embabel.agent.a2a.server.support.A2AEndpointRegistrar;
import com.embabel.common.util.EmbabelObjectMapperHolder;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
@Profile("fixture & api")
@PrimaryAdapter
public class PinbabelA2AConfiguration {

	@Bean
	A2AEndpointRegistrar pinbabelA2AEndpointRegistrar(
		List<AgentCardHandler> handlers,
		RequestMappingHandlerMapping handlerMapping,
		EmbabelObjectMapperHolder objectMapperHolder
	) {
		return new A2AEndpointRegistrar(handlers, handlerMapping, objectMapperHolder);
	}
}
