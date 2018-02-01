package skill;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

public interface NumberProvider {
	public double getMultiModalCost(Node source, Node dest, String mode, double currentDistance, double volume);
	public double getEdgeCost(Node source, Edge e, Node dest);
}

