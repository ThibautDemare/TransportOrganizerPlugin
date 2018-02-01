package skill;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

public class FinancialCosts implements NumberProvider {
	@Override
	public double getEdgeCost(Node source, Edge e, Node dest) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume) {
		// TODO Auto-generated method stub
		return 0;
	}}
