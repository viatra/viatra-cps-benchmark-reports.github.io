package com.viatra.cps.benchmark.reports.processing.verticles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import com.viatra.cps.benchmark.reports.processing.models.AggregatorConfiguration;
import com.viatra.cps.benchmark.reports.processing.models.Diagrams;
import com.viatra.cps.benchmark.reports.processing.models.Message;
import com.viatra.cps.benchmark.reports.processing.models.OperationConfig;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.OperationDescriptor;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.OperationType;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.filter.FilterDescriptor;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.filter.FilterType;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.numeric.NumericOperationDescriptor;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.numeric.NumericOperationType;
import com.viatra.cps.benchmark.reports.processing.verticles.operation.serializer.JSonSerializer;

import eu.mondo.sam.core.results.BenchmarkResult;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class ScenarioVerticle extends AbstractVerticle {

	private Diagrams diagramConfiguration;
	private List<AggregatorConfiguration> configuration;
	private Path outputResultsPath;
	private String buildId;
	private String caseName;
	private String scenario;
	private List<BenchmarkResult> results;
	private ObjectMapper mapper;
	private DeploymentOptions options;
	private Integer numberOfChain = 0;
	private Integer deployedChain = 0;
	private Boolean error = false;
	private List<String> chains;

	public ScenarioVerticle(Diagrams diagramConfiguration, List<AggregatorConfiguration> configuration,
			Path outputResultsPath, String buildId, String caseName, String scenario, List<BenchmarkResult> results,
			ObjectMapper mapper, DeploymentOptions options) {
		this.diagramConfiguration = diagramConfiguration;
		this.configuration = configuration;
		this.outputResultsPath = outputResultsPath;
		this.buildId = buildId;
		this.caseName = caseName;
		this.scenario = scenario;
		this.results = results;
		this.mapper = mapper;
		this.options = options;
		this.chains = new ArrayList<>();

	}

	@Override
	public void start(Future<Void> startFuture) {
		JSonSerializer serializer = new JSonSerializer(this.caseName + "." + this.scenario + ".serializer",
				this.caseName + "." + this.scenario, this.mapper, this.scenario,
				new File(this.outputResultsPath + "/" + this.caseName + "/" + this.scenario + "/results.json"),
				this.diagramConfiguration, this.outputResultsPath + "/" + this.caseName + "/" + this.scenario,
				this.caseName, this.buildId);

		vertx.deployVerticle(serializer, this.options, res -> {
			if (res.succeeded()) {
				Map<String, List<OperationDescriptor>> chains = this.createOperationDescriptorChains(
						this.caseName + "." + this.scenario + ".serializer", this.configuration);
				Set<String> operations = chains.keySet();
				this.numberOfChain = operations.size();
				Integer index = 0;
				for (String operation : operations) {
					List<OperationDescriptor> descriptors = chains.get(operation);
					ChainVerticle verticle = new ChainVerticle(operation, this.scenario, index, descriptors,
							this.mapper, this.options);
					vertx.deployVerticle(verticle, this.options, result -> {
						this.chainDeployed(startFuture, res.succeeded());
						this.deployedChain++;
						this.chains.add(operation);
					});
					index++;
				}
			} else {
				startFuture.fail("Cannot deploy json serializer: " + this.caseName + "." + this.scenario);
			}
		});

		vertx.eventBus().consumer(this.caseName + "." + this.scenario, m -> {
			Message message;
			try {
				message = mapper.readValue(m.body().toString(), Message.class);
				switch (message.getEvent()) {
				case "Start":
					for (String opeation : this.chains) {
						vertx.eventBus().send(this.caseName + "." + this.scenario + "." + opeation, mapper
								.writeValueAsString(new Message("Start", mapper.writeValueAsString(this.results))));
					}
					break;
				case "Done":
					this.deployedChain--;
					if (deployedChain == 0) {
						vertx.eventBus().send(this.caseName + "." + this.scenario + ".serializer",
								mapper.writeValueAsBytes(new Message("Save", "")), res -> {
									try {
										if (res.succeeded()) {

											vertx.eventBus().send(this.caseName,
													mapper.writeValueAsBytes(new Message("Successfull", "")));
										} else {
											vertx.eventBus().send(this.caseName,
													mapper.writeValueAsBytes(new Message("Failed", "")));
										}
									} catch (IOException e) {
										this.sendError();
									}
								});
					}
				default:
					vertx.eventBus().send(this.caseName, m);
				}
			} catch (IOException e) {
				this.sendError();
			}
		});
	}

	protected void sendError() {
		try {
			vertx.eventBus().send(this.caseName, mapper.writeValueAsString(
					new Message("Error", "Cannot parse message in " + this.caseName + "." + this.scenario)));
		} catch (IOException e1) {
			vertx.eventBus().send(this.caseName, "Cannot parse message in " + this.caseName + "." + this.scenario);
		}
	}

	private void chainDeployed(Future<Void> startFuture, Boolean success) {
		if (!success) {
			this.error = true;
		}
		this.numberOfChain--;
		if (numberOfChain == 0) {
			if (this.error) {
				startFuture.fail("Cannot deploy all chain");
			} else {
				startFuture.complete();
			}
		}
	}

	private Map<String, List<OperationDescriptor>> createOperationDescriptorChains(String serializer,
			List<AggregatorConfiguration> configuration) {
		Map<String, List<OperationDescriptor>> chains = new HashMap<>();
		for (AggregatorConfiguration config : configuration) {
			List<OperationDescriptor> descriptors = new ArrayList<>();
			List<OperationConfig> configs = config.getOperations(true);
			OperationDescriptor next = null;
			for (OperationConfig operationConfig : configs) {
				if (configs.indexOf(operationConfig) == 0) {
					OperationDescriptor tmp = this.createOperationDescriptor(serializer, operationConfig,
							config.getID(), configuration.indexOf(config), configs.indexOf(operationConfig));
					descriptors.add(tmp);
					next = tmp;

				} else {
					OperationDescriptor tmp = this.createOperationDescriptor(next.getId(), operationConfig,
							config.getID(), configuration.indexOf(config), configs.indexOf(operationConfig));
					descriptors.add(tmp);
					next = tmp;
				}
			}
			chains.put(config.getID(), descriptors);
		}
		return chains;
	}

	private OperationDescriptor createOperationDescriptor(String next, OperationConfig config, String id,
			Integer chainIndex, Integer index) {
		StringBuilder builder = new StringBuilder();
		builder.append(this.caseName).append('.').append(this.scenario).append('.').append(id).append('.')
				.append(chainIndex).append('.').append(index);
		if (config.getType().equals("Filter")) {
			switch (config.getAttribute()) {
			case "Metric":
				return new FilterDescriptor(buildId.toString(), next, OperationType.FILTER, FilterType.METRIC,
						config.getFilter());
			case "Phase-Name":
				return new FilterDescriptor(buildId.toString(), next, OperationType.FILTER, FilterType.PHASENAME,
						config.getFilter());
			case "RunIndex":
				return new FilterDescriptor(buildId.toString(), next, OperationType.FILTER, FilterType.RUNINDEX,
						config.getFilter());
			case "Tool":
				return new FilterDescriptor(buildId.toString(), next, OperationType.FILTER, FilterType.TOOL,
						config.getFilter());

			default:
				return null;
			}
		} else {
			switch (config.getType()) {
			case "Mean":
				return new NumericOperationDescriptor(id, next, OperationType.NUMERIC, NumericOperationType.MEAN);
			case "Mean-Drop":
				return new NumericOperationDescriptor(id, next, OperationType.NUMERIC, NumericOperationType.MEANDROP);
			case "Average":
				return new NumericOperationDescriptor(id, next, OperationType.NUMERIC, NumericOperationType.AVERAGE);
			case "Summary":
				return new NumericOperationDescriptor(id, next, OperationType.NUMERIC, NumericOperationType.SUMMARY);
			default:
				return null;
			}
		}
	}
}