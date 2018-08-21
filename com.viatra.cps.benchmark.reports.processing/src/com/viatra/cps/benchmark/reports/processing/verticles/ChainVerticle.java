package com.viatra.cps.benchmark.reports.processing.verticles;

import java.io.IOException;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.viatra.cps.benchmark.reports.processing.models.AggregatorConfiguration;
import com.viatra.cps.benchmark.reports.processing.models.Message;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.Operation;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.OperationDescriptor;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.OperationFactory;
import eu.mondo.sam.core.results.BenchmarkResult;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class ChainVerticle extends AbstractVerticle {
	protected String operationId;
	protected String scenarioId;
	protected List<OperationDescriptor> descriptors;
	protected Integer chainSize;
	protected ObjectMapper mapper;
	protected Boolean error;
	protected String id;
	private DeploymentOptions options;

	public ChainVerticle(String operationId, String scenarioId, Integer index, List<OperationDescriptor> descriptors,
			ObjectMapper mapper, DeploymentOptions options) {
		this.operationId = operationId;
		this.scenarioId = scenarioId;
		this.descriptors = descriptors;
		this.chainSize = descriptors.size();
		this.id = scenarioId + "." + index;
		this.mapper = mapper;
		this.options = options;
	}

	@Override
	public void start(Future<Void> startFuture) {
		for (OperationDescriptor descriptor : this.descriptors) {
			Operation operation = OperationFactory.createOperation(descriptor, scenarioId, mapper);
			vertx.deployVerticle(operation, this.options, res -> {
				this.operationDeployed(startFuture, res.succeeded());
			});
		}
		vertx.eventBus().consumer(this.id, m -> {
			Message message;
			try {
				message = mapper.readValue(m.body().toString(), Message.class);
				switch (message.getEvent()) {
				case "Start":
					List<BenchmarkResult> results = mapper.readValue(message.getData(),
							new TypeReference<List<AggregatorConfiguration>>() {
							});
					for (BenchmarkResult result : results) {
						vertx.eventBus().send(this.descriptors.get(0).getId(),
								mapper.writeValueAsString(new Message("Results", mapper.writeValueAsString(result))));
					}
					break;

				default:
					vertx.eventBus().send(this.scenarioId, m);
				}
			} catch (IOException e) {
				this.sendError();
			}
		});

	}

	private void operationDeployed(Future<Void> startFuture, Boolean success) {
		if (!success) {
			this.error = false;
		}
		this.chainSize--;
		if (chainSize == 0) {
			if (this.error) {
				startFuture.fail("Cannot deploy all operation");
			} else {
				startFuture.complete();
			}
		}
	}

	protected void sendError() {
		try {
			vertx.eventBus().send(this.scenarioId,
					mapper.writeValueAsString(new Message("Error", "Cannot parse message in " + this.id)));
		} catch (IOException e1) {
			vertx.eventBus().send(this.scenarioId, "Cannot parse message in " + this.id);
		}
	}
}
