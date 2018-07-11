package at.spot.core.infrastructure.resolver.impex.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import at.spot.core.persistence.query.JpqlQuery;
import at.spot.core.persistence.query.QueryResult;

import at.spot.core.infrastructure.exception.ValueResolverException;
import at.spot.core.infrastructure.resolver.impex.ImpexValueResolver;
import at.spot.core.infrastructure.service.TypeService;
import at.spot.core.infrastructure.support.impex.ColumnDefinition;
import at.spot.core.persistence.service.QueryService;

@Service
public class ReferenceValueResolver implements ImpexValueResolver {

	@Resource
	private TypeService typeService;

	@Resource
	private QueryService queryService;

	@Override
	public <T> T resolve(String value, Class<T> targetType, ColumnDefinition columnDefinition)
			throws ValueResolverException {

		String[] inputParams = value.split(":");

		String desc = columnDefinition.getValueResolutionDescriptor().replace(" ", "");
		List<Node> nodes = parse(desc, 0, value.length());

		String query = "SELECT i FROM " + targetType.getSimpleName() + " i";

		int paramIndex = 0;
		List<String> joinClauses = new ArrayList<>();
		List<String> whereClauses = new ArrayList<>();

		for (Node node : nodes) {
			// this is another complex resolver
			if (node.getNodes().size() > 0) {

			} else {
				whereClauses.add(node.getName() + " = ?" + paramIndex);
				paramIndex++;
			}
		}

		if (inputParams.length != paramIndex) {
			throw new ValueResolverException("Input values doesn't match expected header column definition.");
		}

		if (whereClauses.size() > 0) {
			query += " WHERE " + whereClauses.stream().collect(Collectors.joining(" AND "));
		}

		JpqlQuery<T> qry = new JpqlQuery<>(query, targetType);

		for (int x = 0; x < paramIndex; x++) {
			qry.addParam("" + x, inputParams[x]);
		}

		QueryResult<T> result = queryService.query(qry);

		if (result.getResultList().size() == 1) {
			return result.getResultList().get(0);
		}

		throw new ValueResolverException("Ambiguous results found for given input values.");
	}

	public List<Node> parse(String desc, int start, int end) {
		// I know .. it's not pretty ...
		List<Node> nodes = new ArrayList<>();

		Node tempNode = null;
		String tempToken = "";

		int lastDelimiter = 0;

		for (int x = start; x < end; x++) {
			char c = desc.charAt(x);

			// we found a simple leave node
			if (c == ',') {
				lastDelimiter = x;
				if (StringUtils.isNotBlank(tempToken)) {
					tempNode = new Node(tempToken);
					nodes.add(tempNode);
					System.out.println("Consumed: " + tempToken);
					tempToken = "";
				} else {
					continue;
				}
			} else if (c == '(') {
				// this is a tree node
				if (StringUtils.isNotBlank(tempToken)) {
					tempNode = new Node(tempToken);
					nodes.add(tempNode);
					System.out.println("Consumed: " + tempToken);
					tempToken = "";

					String subDesc = StringUtils.substring(desc, x + 1, desc.length());

					List<Node> children = parse(subDesc, 0, subDesc.length());
					tempNode.nodes.addAll(children);

					// moves the cursor forward -> the node's toString() has to be the exact content
					// of it's consumed text parts
					x = lastDelimiter + tempNode.toString().length() - 1;
				}
			} else if (c == ')') {
				if (StringUtils.isNotBlank(tempToken)) {
					System.out.println("Consumed: " + tempToken);
					tempNode = new Node(tempToken);
					nodes.add(tempNode);
				}
				break;
			} else {
				tempToken += c;
			}
		}

		return nodes;
	}

	public static class Node {
		private String name;
		private final List<Node> nodes = new ArrayList<>();

		public Node(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<Node> getNodes() {
			return nodes;
		}

		@Override
		public String toString() {
			return name + (nodes.size() > 0
					? "(" + nodes.stream().map(n -> n.toString()).collect(Collectors.joining(",")) + ")"
					: "");
		}
	}
}