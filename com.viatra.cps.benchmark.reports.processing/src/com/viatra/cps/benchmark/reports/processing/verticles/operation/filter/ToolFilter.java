package com.viatra.cps.benchmark.reports.processing.verticles.operation.filter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;

import eu.mondo.sam.core.results.BenchmarkResult;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;

public class ToolFilter extends Filter {
	private Map<String, Map<Integer, Map<Integer, List<BenchmarkResult>>>> benchmarkMap;

	public ToolFilter(List<Object> elements, String next, String id, String scenario, ObjectMapper mapper) {
		super(elements, next, id, scenario, mapper);
		this.benchmarkMap = new HashMap<>();
	}

	private void calculate() {
		Set<String> toolKeys = this.benchmarkMap.keySet();
		this.sendResultsSize(this.calculateResultsSize(toolKeys), (AsyncResult<Message<Object>> res) -> {
			toolKeys.forEach(tool -> {
				Set<Integer> sizeKey = this.benchmarkMap.get(tool).keySet();
				sizeKey.forEach(size -> {
					Set<Integer> runKeys = this.benchmarkMap.get(tool).get(size).keySet();
					runKeys.forEach(runIndex -> {
						List<BenchmarkResult> results = this.benchmarkMap.get(tool).get(size).get(runIndex);
						BenchmarkResult filteredResult = createBenchmarkResult(results.get(0));
						results.forEach(result -> {
							result.getPhaseResults().forEach(phaseResult -> {
								filteredResult.addResults(phaseResult);
							});
						});
						this.sendResult(filteredResult);
					});
				});
			});
			return null;
		});
	}

	private String calculateResultsSize(Set<String> toolKeys) {
		Integer size = 0;
		for (String tool : toolKeys) {
			Set<Integer> sizeKey = this.benchmarkMap.get(tool).keySet();
			size += sizeKey.size();
		}
		return size.toString();
	}

	private void addToMap(BenchmarkResult benchmarkResult) {
		Map<Integer, Map<Integer, List<BenchmarkResult>>> toolMap = this.benchmarkMap
				.get(benchmarkResult.getCaseDescriptor().getTool());
		if (toolMap == null) {
			toolMap = new HashMap<>();
			this.benchmarkMap.put(benchmarkResult.getCaseDescriptor().getTool(), toolMap);
		}
		Map<Integer, List<BenchmarkResult>> indexMap = toolMap.get(benchmarkResult.getCaseDescriptor().getSize());
		if (indexMap == null) {
			indexMap = new HashMap<>();
			toolMap.put(benchmarkResult.getCaseDescriptor().getSize(), indexMap);
		}
		List<BenchmarkResult> benchmarkList = indexMap.get(benchmarkResult.getCaseDescriptor().getRunIndex());
		if (benchmarkList == null) {
			benchmarkList = new ArrayList<>();
			indexMap.put(benchmarkResult.getCaseDescriptor().getRunIndex(), benchmarkList);
		}
		benchmarkList.add(benchmarkResult);
	}

	private Boolean isNeeded(BenchmarkResult benchmarkResult, List<Object> elements) {
		Boolean need = elements.stream().filter(element -> {
			return ((String) element).equals(benchmarkResult.getCaseDescriptor().getTool());
		}).findAny().isPresent();
		return need;
	}

	@Override
	public void addResult(BenchmarkResult result) {
		if (this.elements.size() > 0) {
			if (this.isNeeded(result, elements)) {
				this.addToMap(result);
			}
		} else {
			this.addToMap(result);
		}
		this.numberOfResults--;
		if (this.numberOfResults == 0) {
			this.calculate();
		}
	}
}